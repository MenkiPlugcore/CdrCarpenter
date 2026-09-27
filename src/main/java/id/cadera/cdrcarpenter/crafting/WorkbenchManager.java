package id.cadera.cdrcarpenter.crafting;

import id.cadera.cdrcarpenter.CdrCarpenter;
import id.cadera.cdrcarpenter.blueprint.BlueprintDefinition;
import id.cadera.cdrcarpenter.blueprint.BlueprintManager;
import id.cadera.cdrcarpenter.furniture.FurnitureDefinition;
import id.cadera.cdrcarpenter.furniture.FurnitureItemFactory;
import id.cadera.cdrcarpenter.furniture.FurnitureManager;
import id.cadera.cdrcarpenter.furniture.FurnitureRegistry;
import id.cadera.cdrcarpenter.material.CarpenterMaterialFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WorkbenchManager implements Listener {
    private static final int CONFIG_VERSION = 2;
    private static final List<Integer> DEFAULT_RECIPE_SLOTS = List.of(2, 4, 6);
    private static final List<Integer> DEFAULT_INPUT_SLOTS = List.of(10, 12, 14);
    private static final int DEFAULT_RESULT_SLOT = 16;
    private static final String PROCESSED_WOOD_KEY = "cdr:processed_wood";

    private final CdrCarpenter plugin;
    private final FurnitureManager furnitureManager;
    private final FurnitureRegistry registry;
    private final BlueprintManager blueprintManager;
    private final File configFile;
    private final Map<Inventory, Session> sessions = new IdentityHashMap<>();
    private final List<WorkbenchRecipe> recipes = new ArrayList<>();

    private int rows = 3;
    private String title = "Carpenter Workbench";
    private List<Integer> recipeSlots = DEFAULT_RECIPE_SLOTS;
    private List<Integer> inputSlots = DEFAULT_INPUT_SLOTS;
    private int resultSlot = DEFAULT_RESULT_SLOT;

    public WorkbenchManager(
            CdrCarpenter plugin,
            FurnitureManager furnitureManager,
            FurnitureRegistry registry,
            BlueprintManager blueprintManager
    ) {
        this.plugin = plugin;
        this.furnitureManager = furnitureManager;
        this.registry = registry;
        this.blueprintManager = blueprintManager;
        this.configFile = new File(plugin.getDataFolder(), "workbench.yml");
        loadConfig();
    }

    public boolean open(Player player, Interaction interaction) {
        FurnitureDefinition definition = furnitureManager.definitionFor(interaction);
        if (definition == null || !definition.workbenchEnabled()) {
            return false;
        }

        String instanceId = instanceId(interaction);
        if (instanceId == null) {
            return false;
        }
        if (recipes.isEmpty()) {
            player.sendMessage("§cNo Carpenter Workbench recipes are configured.");
            return true;
        }

        Inventory inventory = Bukkit.createInventory(null, rows * 9, title);
        Session session = new Session(instanceId, player.getUniqueId(), firstRecipeFor(player.getUniqueId()));
        sessions.put(inventory, session);
        renderFrame(inventory);
        renderSelectors(inventory, session);
        updateResult(inventory, session);
        player.openInventory(inventory);
        return true;
    }

    public String pickupBlockReason(Interaction interaction) {
        String instanceId = instanceId(interaction);
        if (instanceId == null) {
            return null;
        }
        for (Map.Entry<Inventory, Session> entry : sessions.entrySet()) {
            if (entry.getValue().instanceId().equals(instanceId) && !entry.getKey().getViewers().isEmpty()) {
                return "§eSomeone is using this Carpenter Workbench.";
            }
        }
        return null;
    }

    public void reload() {
        closeAllSessions();
        loadConfig();
    }

    public void shutdown() {
        closeAllSessions();
    }

    private void loadConfig() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        migrateConfig(config);

        rows = Math.max(1, Math.min(config.getInt("gui.rows", 3), 6));
        int size = rows * 9;
        title = config.getString("gui.title", "Carpenter Workbench");
        if (title == null || title.isBlank()) {
            title = "Carpenter Workbench";
        }

        recipeSlots = sanitizeSlots(config.getIntegerList("gui.recipe-slots"), size);
        if (recipeSlots.isEmpty()) {
            recipeSlots = sanitizeSlots(DEFAULT_RECIPE_SLOTS, size);
        }
        inputSlots = sanitizeSlots(config.getIntegerList("gui.input-slots"), size);
        if (inputSlots.isEmpty()) {
            inputSlots = sanitizeSlots(DEFAULT_INPUT_SLOTS, size);
        }

        resultSlot = config.getInt("gui.result-slot", DEFAULT_RESULT_SLOT);
        if (resultSlot < 0 || resultSlot >= size || inputSlots.contains(resultSlot) || recipeSlots.contains(resultSlot)) {
            resultSlot = Math.min(DEFAULT_RESULT_SLOT, size - 1);
        }

        recipes.clear();
        ConfigurationSection root = config.getConfigurationSection("recipes");
        if (root == null) {
            plugin.getLogger().warning("No 'recipes' section found in workbench.yml.");
            return;
        }

        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }
            String id = rawId.toLowerCase(Locale.ROOT);
            String output = section.getString("output", id);
            if (output == null || output.isBlank() || registry.get(output.toLowerCase(Locale.ROOT)) == null) {
                plugin.getLogger().warning("Skipping workbench recipe '" + id + "': invalid furniture output.");
                continue;
            }
            output = output.toLowerCase(Locale.ROOT);

            ConfigurationSection ingredientsSection = section.getConfigurationSection("ingredients");
            if (ingredientsSection == null) {
                plugin.getLogger().warning("Skipping workbench recipe '" + id + "': ingredients are missing.");
                continue;
            }

            Map<String, Integer> ingredients = new LinkedHashMap<>();
            for (String rawIngredient : ingredientsSection.getKeys(false)) {
                int amount = ingredientsSection.getInt(rawIngredient, 0);
                String key = normalizeIngredient(rawIngredient);
                if (key == null || amount <= 0) {
                    plugin.getLogger().warning("Recipe '" + id + "' has invalid ingredient '" + rawIngredient + "'.");
                    continue;
                }
                ingredients.merge(key, amount, Integer::sum);
            }
            if (ingredients.isEmpty()) {
                plugin.getLogger().warning("Skipping workbench recipe '" + id + "': no valid ingredients.");
                continue;
            }
            recipes.add(new WorkbenchRecipe(id, output, ingredients));
        }
        plugin.getLogger().info("Loaded " + recipes.size() + " Carpenter Workbench recipes.");
    }

    private void migrateConfig(YamlConfiguration config) {
        int version = config.getInt("config-version", 1);
        if (version >= CONFIG_VERSION) {
            return;
        }

        ConfigurationSection recipesRoot = config.getConfigurationSection("recipes");
        if (recipesRoot != null) {
            for (String recipeId : List.of("chair", "table", "bookshelf")) {
                ConfigurationSection recipe = recipesRoot.getConfigurationSection(recipeId);
                if (recipe == null) {
                    continue;
                }
                ConfigurationSection ingredients = recipe.getConfigurationSection("ingredients");
                if (ingredients == null || !ingredients.isSet("OAK_PLANKS")) {
                    continue;
                }
                int amount = ingredients.getInt("OAK_PLANKS", 0);
                ingredients.set("OAK_PLANKS", null);
                if (amount > 0) {
                    ingredients.set(PROCESSED_WOOD_KEY, amount);
                }
            }
        }
        config.set("config-version", CONFIG_VERSION);
        try {
            config.save(configFile);
            plugin.getLogger().info("Migrated workbench.yml to config version " + CONFIG_VERSION + ".");
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save migrated workbench.yml: " + exception.getMessage());
        }
    }

    private String normalizeIngredient(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("cdr:")) {
            String customId = trimmed.substring(4).toLowerCase(Locale.ROOT);
            return CarpenterMaterialFactory.PROCESSED_WOOD.equals(customId) ? "cdr:" + customId : null;
        }
        Material material = Material.matchMaterial(trimmed);
        return material == null || material.isAir() ? null : material.name();
    }

    private List<Integer> sanitizeSlots(List<Integer> configured, int size) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (Integer slot : configured) {
            if (slot != null && slot >= 0 && slot < size) {
                result.add(slot);
            }
        }
        return List.copyOf(result);
    }

    private void renderFrame(Inventory inventory) {
        ItemStack filler = filler();
        Set<Integer> functional = new HashSet<>();
        functional.addAll(recipeSlots);
        functional.addAll(inputSlots);
        functional.add(resultSlot);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!functional.contains(slot)) {
                inventory.setItem(slot, filler.clone());
            }
        }
    }

    private void renderSelectors(Inventory inventory, Session session) {
        for (int index = 0; index < recipeSlots.size(); index++) {
            int slot = recipeSlots.get(index);
            if (index >= recipes.size()) {
                inventory.setItem(slot, filler());
                continue;
            }

            WorkbenchRecipe recipe = recipes.get(index);
            FurnitureDefinition output = registry.get(recipe.outputFurnitureId());
            boolean unlocked = canUseRecipe(session.playerId(), recipe.id());
            ItemStack icon;

            if (unlocked) {
                icon = output == null ? new ItemStack(Material.BARRIER) : FurnitureItemFactory.create(plugin, output, 1);
            } else {
                BlueprintDefinition blueprint = blueprintManager.forRecipe(recipe.id());
                icon = blueprint == null ? new ItemStack(Material.PAPER) : blueprintManager.createBlueprint(blueprint.id(), 1);
                if (icon == null) {
                    icon = new ItemStack(Material.PAPER);
                }
            }

            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                if (!unlocked && output != null) {
                    meta.setDisplayName("§cLocked: " + output.displayName());
                }
                List<String> lore = new ArrayList<>();
                if (!unlocked) {
                    BlueprintDefinition blueprint = blueprintManager.forRecipe(recipe.id());
                    lore.add("§cBlueprint required");
                    lore.add("§7Learn §f" + (blueprint == null ? "the matching Blueprint" : blueprint.displayName()) + "§7 first.");
                    lore.add("");
                }
                lore.add("§7Requires:");
                for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
                    lore.add("§f- " + ingredient.getValue() + "x " + prettyIngredient(ingredient.getKey()));
                }
                lore.add("");
                if (recipe.id().equals(session.selectedRecipeId())) {
                    lore.add(unlocked ? "§aSelected recipe" : "§cSelected but locked");
                } else {
                    lore.add(unlocked ? "§eClick to select" : "§8Click to inspect locked recipe");
                }
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inventory.setItem(slot, icon);
        }
    }

    private void updateResult(Inventory inventory, Session session) {
        WorkbenchRecipe recipe = recipeById(session.selectedRecipeId());
        if (recipe == null) {
            inventory.setItem(resultSlot, unavailable("No recipe selected"));
            return;
        }
        if (!canUseRecipe(session.playerId(), recipe.id())) {
            BlueprintDefinition blueprint = blueprintManager.forRecipe(recipe.id());
            inventory.setItem(resultSlot, unavailable(
                    blueprint == null ? "Blueprint required" : "Learn " + blueprint.displayName()
            ));
            return;
        }

        FurnitureDefinition output = registry.get(recipe.outputFurnitureId());
        if (output == null) {
            inventory.setItem(resultSlot, unavailable("Recipe output is unavailable"));
            return;
        }

        boolean ready = hasIngredients(inventory, recipe);
        ItemStack preview = FurnitureItemFactory.create(plugin, output, 1);
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ready ? "§aClick to craft" : "§cMissing materials");
            lore.add("");
            for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
                int present = countIngredient(inventory, ingredient.getKey());
                String color = present >= ingredient.getValue() ? "§a" : "§c";
                lore.add(color + Math.min(present, ingredient.getValue()) + "/" + ingredient.getValue()
                        + " " + prettyIngredient(ingredient.getKey()));
            }
            meta.setLore(lore);
            preview.setItemMeta(meta);
        }
        inventory.setItem(resultSlot, preview);
    }

    private void selectRecipe(Inventory inventory, Session session, int clickedSlot) {
        int index = recipeSlots.indexOf(clickedSlot);
        if (index < 0 || index >= recipes.size()) {
            return;
        }
        session.setSelectedRecipeId(recipes.get(index).id());
        renderSelectors(inventory, session);
        updateResult(inventory, session);
    }

    private void craft(Player player, Inventory inventory, Session session) {
        WorkbenchRecipe recipe = recipeById(session.selectedRecipeId());
        if (recipe == null) {
            return;
        }
        if (!canUseRecipe(player.getUniqueId(), recipe.id())) {
            BlueprintDefinition blueprint = blueprintManager.forRecipe(recipe.id());
            player.sendMessage("§cYou have not learned "
                    + (blueprint == null ? "the required Blueprint" : blueprint.displayName()) + "§c yet.");
            updateResult(inventory, session);
            return;
        }
        if (!hasIngredients(inventory, recipe)) {
            player.sendMessage("§cYou do not have the required materials in the Workbench input slots.");
            updateResult(inventory, session);
            return;
        }

        FurnitureDefinition output = registry.get(recipe.outputFurnitureId());
        if (output == null) {
            player.sendMessage("§cThat furniture recipe is currently unavailable.");
            return;
        }

        consumeIngredients(inventory, recipe);
        ItemStack crafted = FurnitureItemFactory.create(plugin, output, 1);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(crafted);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage("§aCrafted §f" + output.displayName() + "§a.");
        updateResult(inventory, session);
    }

    private boolean canUseRecipe(UUID playerId, String recipeId) {
        return blueprintManager.forRecipe(recipeId) == null || blueprintManager.isRecipeUnlocked(playerId, recipeId);
    }

    private String firstRecipeFor(UUID playerId) {
        for (WorkbenchRecipe recipe : recipes) {
            if (canUseRecipe(playerId, recipe.id())) {
                return recipe.id();
            }
        }
        return recipes.getFirst().id();
    }

    private boolean hasIngredients(Inventory inventory, WorkbenchRecipe recipe) {
        for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
            if (countIngredient(inventory, ingredient.getKey()) < ingredient.getValue()) {
                return false;
            }
        }
        return true;
    }

    private int countIngredient(Inventory inventory, String key) {
        int count = 0;
        for (int slot : inputSlots) {
            ItemStack item = inventory.getItem(slot);
            if (matchesIngredient(item, key)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private boolean matchesIngredient(ItemStack item, String key) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (key.startsWith("cdr:")) {
            return CarpenterMaterialFactory.is(plugin, item, key.substring(4));
        }
        Material material = Material.matchMaterial(key);
        return material != null && item.getType() == material;
    }

    private void consumeIngredients(Inventory inventory, WorkbenchRecipe recipe) {
        for (Map.Entry<String, Integer> ingredient : recipe.ingredients().entrySet()) {
            int remaining = ingredient.getValue();
            for (int slot : inputSlots) {
                if (remaining <= 0) {
                    break;
                }
                ItemStack item = inventory.getItem(slot);
                if (!matchesIngredient(item, ingredient.getKey())) {
                    continue;
                }
                int consume = Math.min(remaining, item.getAmount());
                int left = item.getAmount() - consume;
                if (left <= 0) {
                    inventory.setItem(slot, null);
                } else {
                    item.setAmount(left);
                }
                remaining -= consume;
            }
        }
    }

    private void transferIntoInputs(Inventory inventory, InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }
        ItemStack moving = current.clone();
        for (int slot : inputSlots) {
            ItemStack target = inventory.getItem(slot);
            if (target == null || target.getType().isAir() || !target.isSimilar(moving)) {
                continue;
            }
            int room = Math.min(target.getMaxStackSize(), inventory.getMaxStackSize()) - target.getAmount();
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, moving.getAmount());
            target.setAmount(target.getAmount() + moved);
            moving.setAmount(moving.getAmount() - moved);
            if (moving.getAmount() <= 0) {
                event.setCurrentItem(null);
                return;
            }
        }
        for (int slot : inputSlots) {
            ItemStack target = inventory.getItem(slot);
            if (target != null && !target.getType().isAir()) {
                continue;
            }
            int moved = Math.min(moving.getAmount(), moving.getMaxStackSize());
            ItemStack placed = moving.clone();
            placed.setAmount(moved);
            inventory.setItem(slot, placed);
            moving.setAmount(moving.getAmount() - moved);
            if (moving.getAmount() <= 0) {
                event.setCurrentItem(null);
                return;
            }
        }
        event.setCurrentItem(moving);
    }

    private void returnInputs(Player player, Inventory inventory) {
        for (int slot : inputSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            inventory.setItem(slot, null);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    private WorkbenchRecipe recipeById(String id) {
        for (WorkbenchRecipe recipe : recipes) {
            if (recipe.id().equals(id)) {
                return recipe;
            }
        }
        return null;
    }

    private String prettyIngredient(String key) {
        if (key.startsWith("cdr:")) {
            return CarpenterMaterialFactory.displayName(key.substring(4));
        }
        Material material = Material.matchMaterial(key);
        if (material == null) {
            return key;
        }
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack unavailable(String message) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c" + message);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String instanceId(Interaction interaction) {
        return interaction.getPersistentDataContainer().get(plugin.instanceIdKey(), PersistentDataType.STRING);
    }

    private void scheduleResultUpdate(Inventory inventory) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Session current = sessions.get(inventory);
            if (current != null) {
                updateResult(inventory, current);
            }
        });
    }

    private void closeAllSessions() {
        Set<UUID> viewers = new LinkedHashSet<>();
        for (Inventory inventory : new ArrayList<>(sessions.keySet())) {
            inventory.getViewers().forEach(viewer -> {
                if (viewer instanceof Player player) {
                    viewers.add(player.getUniqueId());
                }
            });
        }
        for (UUID playerId : viewers) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.closeInventory();
            }
        }
        sessions.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        Session session = sessions.get(top);
        if (session == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!session.playerId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < top.getSize()) {
            if (recipeSlots.contains(rawSlot)) {
                event.setCancelled(true);
                selectRecipe(top, session, rawSlot);
                return;
            }
            if (rawSlot == resultSlot) {
                event.setCancelled(true);
                craft(player, top, session);
                return;
            }
            if (!inputSlots.contains(rawSlot)) {
                event.setCancelled(true);
                return;
            }
            scheduleResultUpdate(top);
            return;
        }

        if (event.isShiftClick() && event.getClickedInventory() != null) {
            event.setCancelled(true);
            transferIntoInputs(top, event);
            updateResult(top, session);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        Session session = sessions.get(top);
        if (session == null) {
            return;
        }
        if (!session.playerId().equals(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize() && !inputSlots.contains(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
        scheduleResultUpdate(top);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (sessions.remove(inventory) == null) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            returnInputs(player, inventory);
        }
    }

    private static final class Session {
        private final String instanceId;
        private final UUID playerId;
        private String selectedRecipeId;

        private Session(String instanceId, UUID playerId, String selectedRecipeId) {
            this.instanceId = instanceId;
            this.playerId = playerId;
            this.selectedRecipeId = selectedRecipeId;
        }

        private String instanceId() {
            return instanceId;
        }

        private UUID playerId() {
            return playerId;
        }

        private String selectedRecipeId() {
            return selectedRecipeId;
        }

        private void setSelectedRecipeId(String selectedRecipeId) {
            this.selectedRecipeId = selectedRecipeId;
        }
    }
}
