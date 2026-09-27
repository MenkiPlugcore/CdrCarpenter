package id.cadera.cdrcarpenter.crafting;

import id.cadera.cdrcarpenter.CdrCarpenter;
import id.cadera.cdrcarpenter.furniture.FurnitureDefinition;
import id.cadera.cdrcarpenter.furniture.FurnitureManager;
import id.cadera.cdrcarpenter.material.CarpenterMaterialFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SawmillManager implements Listener {
    private final CdrCarpenter plugin;
    private final FurnitureManager furnitureManager;
    private final File configFile;
    private final Map<Inventory, Session> sessions = new IdentityHashMap<>();
    private final Set<Material> acceptedInputs = new LinkedHashSet<>();

    private int rows = 3;
    private String title = "Carpenter Sawmill";
    private int inputSlot = 11;
    private int actionSlot = 13;
    private int outputSlot = 15;
    private String outputItemId = CarpenterMaterialFactory.PROCESSED_WOOD;
    private int outputPerLog = 4;

    public SawmillManager(CdrCarpenter plugin, FurnitureManager furnitureManager) {
        this.plugin = plugin;
        this.furnitureManager = furnitureManager;
        this.configFile = new File(plugin.getDataFolder(), "sawmill.yml");
        loadConfig();
    }

    public boolean open(Player player, Interaction interaction) {
        FurnitureDefinition definition = furnitureManager.definitionFor(interaction);
        if (definition == null || !definition.sawmillEnabled()) {
            return false;
        }

        String instanceId = instanceId(interaction);
        if (instanceId == null) {
            return false;
        }

        Inventory inventory = Bukkit.createInventory(null, rows * 9, title);
        sessions.put(inventory, new Session(instanceId));
        renderFrame(inventory);
        updateGui(inventory);
        player.openInventory(inventory);
        return true;
    }

    public String pickupBlockReason(Interaction interaction) {
        String instanceId = instanceId(interaction);
        if (instanceId == null) {
            return null;
        }

        for (Map.Entry<Inventory, Session> entry : sessions.entrySet()) {
            if (!entry.getValue().instanceId().equals(instanceId)) {
                continue;
            }
            if (!entry.getKey().getViewers().isEmpty()) {
                return "§eSomeone is using this Carpenter Sawmill.";
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
        rows = Math.max(1, Math.min(config.getInt("gui.rows", 3), 6));
        int size = rows * 9;

        title = config.getString("gui.title", "Carpenter Sawmill");
        if (title == null || title.isBlank()) {
            title = "Carpenter Sawmill";
        }

        inputSlot = sanitizeSlot(config.getInt("gui.input-slot", 11), size, 11);
        actionSlot = sanitizeSlot(config.getInt("gui.action-slot", 13), size, 13);
        outputSlot = sanitizeSlot(config.getInt("gui.output-slot", 15), size, 15);

        if (actionSlot == inputSlot) {
            actionSlot = firstFreeSlot(size, Set.of(inputSlot));
        }
        if (outputSlot == inputSlot || outputSlot == actionSlot) {
            outputSlot = firstFreeSlot(size, Set.of(inputSlot, actionSlot));
        }

        outputItemId = config.getString("processing.output-item", CarpenterMaterialFactory.PROCESSED_WOOD);
        if (outputItemId == null || outputItemId.isBlank()) {
            outputItemId = CarpenterMaterialFactory.PROCESSED_WOOD;
        }
        outputItemId = outputItemId.toLowerCase(Locale.ROOT);
        outputPerLog = Math.max(1, Math.min(config.getInt("processing.output-per-log", 4), 64));

        acceptedInputs.clear();
        for (String raw : config.getStringList("processing.accepted-inputs")) {
            Material material = Material.matchMaterial(raw);
            if (material == null || material.isAir()) {
                plugin.getLogger().warning("Sawmill has invalid accepted input '" + raw + "'.");
                continue;
            }
            acceptedInputs.add(material);
        }

        if (acceptedInputs.isEmpty()) {
            acceptedInputs.add(Material.OAK_LOG);
        }

        plugin.getLogger().info("Loaded Carpenter Sawmill config with " + acceptedInputs.size()
                + " accepted log types; output=" + outputPerLog + "x " + outputItemId + ".");
    }

    private int sanitizeSlot(int configured, int size, int fallback) {
        if (configured >= 0 && configured < size) {
            return configured;
        }
        return Math.max(0, Math.min(fallback, size - 1));
    }

    private int firstFreeSlot(int size, Set<Integer> occupied) {
        for (int slot = 0; slot < size; slot++) {
            if (!occupied.contains(slot)) {
                return slot;
            }
        }
        return 0;
    }

    private void renderFrame(Inventory inventory) {
        ItemStack filler = filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == inputSlot || slot == actionSlot || slot == outputSlot) {
                continue;
            }
            inventory.setItem(slot, filler.clone());
        }
    }

    private void updateGui(Inventory inventory) {
        ItemStack input = inventory.getItem(inputSlot);
        boolean valid = isAcceptedInput(input);

        ItemStack button = new ItemStack(valid ? Material.STONECUTTER : Material.BARRIER);
        ItemMeta buttonMeta = button.getItemMeta();
        if (buttonMeta != null) {
            buttonMeta.setDisplayName(valid ? "§aCut Wood" : "§cInsert a valid log");
            buttonMeta.setLore(valid
                    ? java.util.List.of(
                            "§7Consumes 1 log per click.",
                            "§7Shift-click to process the whole stack.",
                            "",
                            "§eClick to process"
                    )
                    : java.util.List.of("§7Put a supported log in the input slot."));
            button.setItemMeta(buttonMeta);
        }
        inventory.setItem(actionSlot, button);

        ItemStack preview = CarpenterMaterialFactory.create(plugin, outputItemId, outputPerLog);
        if (preview == null) {
            preview = new ItemStack(Material.BARRIER);
            ItemMeta meta = preview.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§cInvalid sawmill output");
                preview.setItemMeta(meta);
            }
        } else {
            ItemMeta meta = preview.getItemMeta();
            if (meta != null) {
                meta.setLore(java.util.List.of(
                        "§7Output per log: §f" + outputPerLog,
                        valid ? "§aReady to cut" : "§8Waiting for input"
                ));
                preview.setItemMeta(meta);
            }
        }
        inventory.setItem(outputSlot, preview);
    }

    private void process(Player player, Inventory inventory, boolean wholeStack) {
        ItemStack input = inventory.getItem(inputSlot);
        if (!isAcceptedInput(input)) {
            player.sendMessage("§cInsert a valid log into the Sawmill first.");
            updateGui(inventory);
            return;
        }

        int logsToProcess = wholeStack ? input.getAmount() : 1;
        int remaining = input.getAmount() - logsToProcess;
        if (remaining <= 0) {
            inventory.setItem(inputSlot, null);
        } else {
            input.setAmount(remaining);
        }

        int totalOutput = logsToProcess * outputPerLog;
        int remainingOutput = totalOutput;
        while (remainingOutput > 0) {
            int stackAmount = Math.min(64, remainingOutput);
            ItemStack output = CarpenterMaterialFactory.create(plugin, outputItemId, stackAmount);
            if (output == null) {
                player.sendMessage("§cSawmill output item is not configured correctly.");
                break;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(output);
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            remainingOutput -= stackAmount;
        }

        player.sendMessage("§aProcessed §f" + logsToProcess + " log"
                + (logsToProcess == 1 ? "" : "s") + " §ainto §f" + totalOutput + " Processed Wood Board"
                + (totalOutput == 1 ? "" : "s") + "§a.");
        updateGui(inventory);
    }

    private boolean isAcceptedInput(ItemStack item) {
        return item != null && !item.getType().isAir() && acceptedInputs.contains(item.getType());
    }

    private void transferIntoInput(Inventory top, InventoryClickEvent event, Player player) {
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }
        if (!isAcceptedInput(current)) {
            player.sendMessage("§cThat item cannot be processed by this Sawmill.");
            return;
        }

        ItemStack target = top.getItem(inputSlot);
        if (target == null || target.getType().isAir()) {
            top.setItem(inputSlot, current.clone());
            event.setCurrentItem(null);
            return;
        }

        if (!target.isSimilar(current)) {
            player.sendMessage("§cThe Sawmill input slot already contains a different log type.");
            return;
        }

        int max = Math.min(target.getMaxStackSize(), top.getMaxStackSize());
        int room = max - target.getAmount();
        if (room <= 0) {
            return;
        }
        int moved = Math.min(room, current.getAmount());
        target.setAmount(target.getAmount() + moved);
        current.setAmount(current.getAmount() - moved);
        if (current.getAmount() <= 0) {
            event.setCurrentItem(null);
        }
    }

    private void returnInput(Player player, Inventory inventory) {
        ItemStack input = inventory.getItem(inputSlot);
        if (input == null || input.getType().isAir()) {
            return;
        }
        inventory.setItem(inputSlot, null);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(input);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
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

    private String instanceId(Interaction interaction) {
        return interaction.getPersistentDataContainer().get(plugin.instanceIdKey(), PersistentDataType.STRING);
    }

    private void scheduleUpdate(Inventory inventory) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (sessions.containsKey(inventory)) {
                updateGui(inventory);
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

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < top.getSize()) {
            if (rawSlot == actionSlot) {
                event.setCancelled(true);
                process(player, top, event.isShiftClick());
                return;
            }
            if (rawSlot == outputSlot) {
                event.setCancelled(true);
                return;
            }
            if (rawSlot != inputSlot) {
                event.setCancelled(true);
                return;
            }

            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !isAcceptedInput(cursor)) {
                event.setCancelled(true);
                player.sendMessage("§cThat item cannot be processed by this Sawmill.");
                return;
            }
            scheduleUpdate(top);
            return;
        }

        if (event.isShiftClick() && event.getClickedInventory() != null) {
            event.setCancelled(true);
            transferIntoInput(top, event, player);
            updateGui(top);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!sessions.containsKey(top)) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize() && rawSlot != inputSlot) {
                event.setCancelled(true);
                return;
            }
        }

        ItemStack cursor = event.getOldCursor();
        if (cursor != null && !cursor.getType().isAir() && !isAcceptedInput(cursor)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage("§cThat item cannot be processed by this Sawmill.");
            }
            return;
        }
        scheduleUpdate(top);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (sessions.remove(inventory) == null) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            returnInput(player, inventory);
        }
    }

    private record Session(String instanceId) {
    }
}
