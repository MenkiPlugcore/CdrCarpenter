package id.cadera.cdrcarpenter.blueprint;

import id.cadera.cdrcarpenter.CdrCarpenter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BlueprintManager implements Listener {
    private final CdrCarpenter plugin;
    private final File configFile;
    private final File dataFile;
    private final Map<String, BlueprintDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, String> blueprintByRecipe = new HashMap<>();
    private final Map<UUID, Set<String>> unlockedByPlayer = new HashMap<>();
    private YamlConfiguration data;

    public BlueprintManager(CdrCarpenter plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "blueprints.yml");
        this.dataFile = new File(plugin.getDataFolder(), "blueprint-data.yml");
        loadDefinitions();
        loadData();
    }

    public void reload() {
        loadDefinitions();
        loadData();
    }

    public Collection<BlueprintDefinition> all() {
        return List.copyOf(definitions.values());
    }

    public BlueprintDefinition get(String id) {
        if (id == null) {
            return null;
        }
        return definitions.get(id.toLowerCase(Locale.ROOT));
    }

    public BlueprintDefinition forRecipe(String recipeId) {
        if (recipeId == null) {
            return null;
        }
        String blueprintId = blueprintByRecipe.get(recipeId.toLowerCase(Locale.ROOT));
        return blueprintId == null ? null : definitions.get(blueprintId);
    }

    public boolean isRecipeUnlocked(Player player, String recipeId) {
        return isRecipeUnlocked(player.getUniqueId(), recipeId);
    }

    public boolean isRecipeUnlocked(UUID playerId, String recipeId) {
        if (recipeId == null) {
            return false;
        }
        return unlockedByPlayer.getOrDefault(playerId, Set.of())
                .contains(recipeId.toLowerCase(Locale.ROOT));
    }

    public Set<String> unlockedRecipes(UUID playerId) {
        return Set.copyOf(unlockedByPlayer.getOrDefault(playerId, Set.of()));
    }

    public ItemStack createBlueprint(String blueprintId, int amount) {
        BlueprintDefinition definition = get(blueprintId);
        if (definition == null) {
            return null;
        }

        int safeAmount = Math.max(1, Math.min(64, amount));
        ItemStack item = null;
        if (definition.itemsAdderId() != null && !definition.itemsAdderId().isBlank()) {
            item = plugin.itemsAdderBridge().createItem(definition.itemsAdderId(), safeAmount);
        }
        if (item == null) {
            item = new ItemStack(Material.PAPER, safeAmount);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b" + definition.displayName());
            List<String> lore = new ArrayList<>();
            lore.add("§7Right-click to learn permanently.");
            lore.add("§7Unlocks Workbench recipe: §f" + pretty(definition.recipeId()));
            lore.add("");
            lore.add("§8Tradeable until learned.");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(
                    plugin.blueprintIdKey(),
                    PersistentDataType.STRING,
                    definition.id()
            );
            item.setItemMeta(meta);
        }
        item.setAmount(safeAmount);
        return item;
    }

    public String getBlueprintId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(plugin.blueprintIdKey(), PersistentDataType.STRING);
    }

    public LearnResult learn(Player player, String blueprintId) {
        BlueprintDefinition definition = get(blueprintId);
        if (definition == null) {
            return LearnResult.INVALID;
        }

        Set<String> unlocked = unlockedByPlayer.computeIfAbsent(
                player.getUniqueId(),
                ignored -> new LinkedHashSet<>()
        );
        if (!unlocked.add(definition.recipeId())) {
            return LearnResult.ALREADY_KNOWN;
        }

        savePlayer(player.getUniqueId(), unlocked);
        return LearnResult.LEARNED;
    }

    private void loadDefinitions() {
        definitions.clear();
        blueprintByRecipe.clear();

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection root = config.getConfigurationSection("blueprints");
        if (root == null) {
            plugin.getLogger().warning("No 'blueprints' section found in blueprints.yml.");
            return;
        }

        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }

            String id = rawId.toLowerCase(Locale.ROOT);
            String displayName = section.getString("display-name", "Blueprint: " + pretty(id));
            String recipeId = section.getString("recipe", id);
            if (recipeId == null || recipeId.isBlank()) {
                plugin.getLogger().warning("Skipping blueprint '" + id + "': recipe is missing.");
                continue;
            }
            recipeId = recipeId.toLowerCase(Locale.ROOT);

            String itemsAdderId = section.getString("itemsadder-id", "cdrcarpenter:blueprint_" + id);
            if (itemsAdderId != null && itemsAdderId.isBlank()) {
                itemsAdderId = null;
            }

            BlueprintDefinition definition = new BlueprintDefinition(
                    id,
                    displayName == null || displayName.isBlank() ? "Blueprint: " + pretty(id) : displayName,
                    recipeId,
                    itemsAdderId
            );
            definitions.put(id, definition);
            blueprintByRecipe.put(recipeId, id);
        }

        plugin.getLogger().info("Loaded " + definitions.size() + " Carpenter Blueprints.");
    }

    private void loadData() {
        data = YamlConfiguration.loadConfiguration(dataFile);
        unlockedByPlayer.clear();

        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return;
        }

        for (String rawUuid : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                List<String> configured = data.getStringList("players." + rawUuid + ".unlocked");
                Set<String> unlocked = new LinkedHashSet<>();
                for (String recipeId : configured) {
                    if (recipeId != null && !recipeId.isBlank()) {
                        unlocked.add(recipeId.toLowerCase(Locale.ROOT));
                    }
                }
                unlockedByPlayer.put(uuid, unlocked);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid UUID in blueprint-data.yml: " + rawUuid);
            }
        }
    }

    private void savePlayer(UUID uuid, Set<String> unlocked) {
        data.set("players." + uuid + ".unlocked", new ArrayList<>(unlocked));
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save blueprint-data.yml: " + exception.getMessage());
        }
    }

    private void consumeOne(Player player, EquipmentSlot hand) {
        ItemStack held = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            return;
        }
        if (held.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            held.setAmount(held.getAmount() - 1);
        }
    }

    private String pretty(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        String[] parts = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() == null) {
            return;
        }

        String blueprintId = getBlueprintId(event.getItem());
        if (blueprintId == null) {
            return;
        }

        event.setCancelled(true);
        BlueprintDefinition definition = get(blueprintId);
        if (definition == null) {
            event.getPlayer().sendMessage("§cThis Blueprint is no longer configured.");
            return;
        }

        LearnResult result = learn(event.getPlayer(), blueprintId);
        switch (result) {
            case LEARNED -> {
                consumeOne(event.getPlayer(), event.getHand());
                event.getPlayer().sendMessage("§aBlueprint learned: §f" + definition.displayName() + "§a.");
                event.getPlayer().sendMessage("§7The §f" + pretty(definition.recipeId()) + " §7recipe is now permanently unlocked.");
                plugin.feedback().blueprintLearned(event.getPlayer(), definition.displayName());
            }
            case ALREADY_KNOWN -> {
                event.getPlayer().sendMessage("§eYou already know this Blueprint. The item was not consumed.");
                plugin.feedback().blueprintAlreadyKnown(event.getPlayer());
            }
            case INVALID -> event.getPlayer().sendMessage("§cThis Blueprint is invalid.");
        }
    }

    public enum LearnResult {
        LEARNED,
        ALREADY_KNOWN,
        INVALID
    }
}
