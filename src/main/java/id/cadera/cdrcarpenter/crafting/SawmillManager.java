package id.cadera.cdrcarpenter.crafting;

import id.cadera.cdrcarpenter.CdrCarpenter;
import id.cadera.cdrcarpenter.furniture.FurnitureDefinition;
import id.cadera.cdrcarpenter.furniture.FurnitureManager;
import id.cadera.cdrcarpenter.material.CarpenterMaterialFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
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
import org.bukkit.scheduler.BukkitTask;

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
    private int durationTicks = 50;
    private int pulseIntervalTicks = 10;

    public SawmillManager(CdrCarpenter plugin, FurnitureManager furnitureManager) {
        this.plugin = plugin;
        this.furnitureManager = furnitureManager;
        this.configFile = new File(plugin.getDataFolder(), "sawmill.yml");
        loadConfig();
    }

    public boolean open(Player player, Interaction interaction) {
        FurnitureDefinition definition = furnitureManager.definitionFor(interaction);
        if (definition == null || !"sawmill".equals(definition.id())) {
            return false;
        }

        String instanceId = instanceId(interaction);
        if (instanceId == null) {
            return false;
        }

        Inventory inventory = Bukkit.createInventory(null, rows * 9, title);
        Session session = new Session(instanceId, player.getUniqueId(), interaction.getLocation().clone());
        sessions.put(inventory, session);
        renderFrame(inventory);
        updateGui(inventory, session);
        player.openInventory(inventory);
        return true;
    }

    public String pickupBlockReason(Interaction interaction) {
        String instanceId = instanceId(interaction);
        if (instanceId == null) {
            return null;
        }
        for (Map.Entry<Inventory, Session> entry : sessions.entrySet()) {
            if (entry.getValue().instanceId.equals(instanceId) && !entry.getKey().getViewers().isEmpty()) {
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

        durationTicks = Math.max(10, Math.min(config.getInt("processing.duration-ticks", 50), 20 * 60));
        pulseIntervalTicks = Math.max(2, Math.min(config.getInt("processing.pulse-interval-ticks", 10), durationTicks));

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
                + " accepted log types; duration=" + durationTicks + " ticks; output="
                + outputPerLog + "x " + outputItemId + ".");
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

    private void updateGui(Inventory inventory, Session session) {
        ItemStack input = inventory.getItem(inputSlot);
        boolean valid = isAcceptedInput(input);

        ItemStack button;
        if (session.processing) {
            int progress = progressPercent(session);
            button = new ItemStack(Material.CLOCK);
            ItemMeta meta = button.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§eCutting... §f" + progress + "%");
                meta.setLore(java.util.List.of(
                        "§7Logs in batch: §f" + session.logsToProcess,
                        progressBar(progress),
                        "",
                        "§8Close the GUI to cancel safely."
                ));
                button.setItemMeta(meta);
            }
        } else {
            button = new ItemStack(valid ? Material.STONECUTTER : Material.BARRIER);
            ItemMeta buttonMeta = button.getItemMeta();
            if (buttonMeta != null) {
                buttonMeta.setDisplayName(valid ? "§aStart Cutting" : "§cInsert a valid log");
                buttonMeta.setLore(valid
                        ? java.util.List.of(
                        "§7Processing time: §f" + String.format(Locale.US, "%.1f", durationTicks / 20.0D) + "s",
                        "§7Click: process 1 log",
                        "§7Shift-click: process the whole stack",
                        "",
                        "§eClick to start"
                )
                        : java.util.List.of("§7Put a supported log in the input slot."));
                button.setItemMeta(buttonMeta);
            }
        }
        inventory.setItem(actionSlot, button);

        int previewAmount = session.processing
                ? Math.max(1, Math.min(64, session.logsToProcess * outputPerLog))
                : outputPerLog;
        ItemStack preview = CarpenterMaterialFactory.create(plugin, outputItemId, previewAmount);
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
                if (session.processing) {
                    meta.setLore(java.util.List.of(
                            "§7Batch output: §f" + (session.logsToProcess * outputPerLog),
                            "§eProcessing... " + progressPercent(session) + "%"
                    ));
                } else {
                    meta.setLore(java.util.List.of(
                            "§7Output per log: §f" + outputPerLog,
                            valid ? "§aReady to process" : "§8Waiting for input"
                    ));
                }
                preview.setItemMeta(meta);
            }
        }
        inventory.setItem(outputSlot, preview);
    }

    private void startProcess(Player player, Inventory inventory, Session session, boolean wholeStack) {
        if (session.processing) {
            player.sendMessage("§eThe Sawmill is already running.");
            return;
        }

        ItemStack input = inventory.getItem(inputSlot);
        if (!isAcceptedInput(input)) {
            player.sendMessage("§cInsert a valid log into the Sawmill first.");
            plugin.feedback().workbenchFailure(player);
            updateGui(inventory, session);
            return;
        }

        session.processing = true;
        session.logsToProcess = wholeStack ? input.getAmount() : 1;
        session.elapsedTicks = 0;
        session.frame = false;

        plugin.feedback().sawmillStart(player, session.location);
        setSawFrame(session, true);
        updateGui(inventory, session);

        session.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickProcess(inventory, session),
                pulseIntervalTicks, pulseIntervalTicks);
    }

    private void tickProcess(Inventory inventory, Session session) {
        if (!sessions.containsKey(inventory) || !session.processing) {
            cancelTask(session);
            return;
        }

        Player player = Bukkit.getPlayer(session.playerId);
        if (player == null || !player.isOnline()) {
            cancelProcessing(session);
            return;
        }

        session.elapsedTicks = Math.min(durationTicks, session.elapsedTicks + pulseIntervalTicks);
        session.frame = !session.frame;
        setSawFrame(session, session.frame);

        int progress = progressPercent(session);
        plugin.feedback().sawmillPulse(player, session.location, progress);
        updateGui(inventory, session);

        if (session.elapsedTicks >= durationTicks) {
            finishProcess(player, inventory, session);
        }
    }

    private void finishProcess(Player player, Inventory inventory, Session session) {
        ItemStack input = inventory.getItem(inputSlot);
        if (!isAcceptedInput(input) || input.getAmount() < session.logsToProcess) {
            player.sendMessage("§cSawmill input changed before processing finished. Nothing was consumed.");
            cancelProcessing(session);
            updateGui(inventory, session);
            return;
        }

        int remaining = input.getAmount() - session.logsToProcess;
        if (remaining <= 0) {
            inventory.setItem(inputSlot, null);
        } else {
            input.setAmount(remaining);
        }

        int totalOutput = session.logsToProcess * outputPerLog;
        giveOutput(player, totalOutput);
        player.sendMessage("§aProcessed §f" + session.logsToProcess + " log"
                + (session.logsToProcess == 1 ? "" : "s") + " §ainto §f" + totalOutput
                + " Processed Wood Board" + (totalOutput == 1 ? "" : "s") + "§a.");

        plugin.feedback().sawmillFinish(player, session.location, totalOutput);
        session.processing = false;
        session.logsToProcess = 0;
        session.elapsedTicks = 0;
        cancelTask(session);
        restoreSawModel(session);
        updateGui(inventory, session);
    }

    private void giveOutput(Player player, int amount) {
        int remainingOutput = amount;
        while (remainingOutput > 0) {
            int stackAmount = Math.min(64, remainingOutput);
            ItemStack output = CarpenterMaterialFactory.create(plugin, outputItemId, stackAmount);
            if (output == null) {
                player.sendMessage("§cSawmill output item is not configured correctly.");
                return;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(output);
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            remainingOutput -= stackAmount;
        }
    }

    private int progressPercent(Session session) {
        if (!session.processing || durationTicks <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, (int) Math.round(session.elapsedTicks * 100.0D / durationTicks)));
    }

    private String progressBar(int percent) {
        int filled = Math.max(0, Math.min(10, percent / 10));
        StringBuilder bar = new StringBuilder("§a");
        for (int i = 0; i < filled; i++) {
            bar.append('■');
        }
        bar.append("§8");
        for (int i = filled; i < 10; i++) {
            bar.append('■');
        }
        return bar.toString();
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
            Session session = sessions.get(inventory);
            if (session != null) {
                updateGui(inventory, session);
            }
        });
    }

    private void setSawFrame(Session session, boolean alternate) {
        if (!plugin.feedback().sawmillModelAnimationEnabled() || !plugin.itemsAdderBridge().isAvailable()) {
            return;
        }
        String itemId = alternate ? "cdrcarpenter:sawmill_active_b" : "cdrcarpenter:sawmill_active_a";
        ItemStack item = plugin.itemsAdderBridge().createItem(itemId, 1);
        if (item == null) {
            return;
        }
        findDisplay(session, item);
    }

    private void restoreSawModel(Session session) {
        if (!plugin.itemsAdderBridge().isAvailable()) {
            return;
        }
        ItemStack item = plugin.itemsAdderBridge().createItem("cdrcarpenter:sawmill", 1);
        if (item != null) {
            findDisplay(session, item);
        }
    }

    private void findDisplay(Session session, ItemStack item) {
        if (session.location.getWorld() == null) {
            return;
        }
        for (Entity entity : session.location.getWorld().getNearbyEntities(session.location, 3.0D, 2.5D, 3.0D)) {
            if (!(entity instanceof ItemDisplay display)) {
                continue;
            }
            String candidate = display.getPersistentDataContainer()
                    .get(plugin.instanceIdKey(), PersistentDataType.STRING);
            if (session.instanceId.equals(candidate)) {
                display.setItemStack(item);
                return;
            }
        }
    }

    private void cancelTask(Session session) {
        BukkitTask task = session.task;
        session.task = null;
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelProcessing(Session session) {
        session.processing = false;
        session.logsToProcess = 0;
        session.elapsedTicks = 0;
        cancelTask(session);
        restoreSawModel(session);
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
        for (Session session : sessions.values()) {
            cancelProcessing(session);
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
        if (session.processing) {
            if (rawSlot >= 0 && rawSlot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        if (rawSlot >= 0 && rawSlot < top.getSize()) {
            if (rawSlot == actionSlot) {
                event.setCancelled(true);
                startProcess(player, top, session, event.isShiftClick());
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
            updateGui(top, session);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        Session session = sessions.get(top);
        if (session == null) {
            return;
        }
        if (session.processing) {
            if (event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
                event.setCancelled(true);
            }
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
        Session session = sessions.remove(inventory);
        if (session == null) {
            return;
        }
        cancelProcessing(session);
        if (event.getPlayer() instanceof Player player) {
            returnInput(player, inventory);
        }
    }

    private static final class Session {
        private final String instanceId;
        private final UUID playerId;
        private final Location location;
        private boolean processing;
        private int logsToProcess;
        private int elapsedTicks;
        private boolean frame;
        private BukkitTask task;

        private Session(String instanceId, UUID playerId, Location location) {
            this.instanceId = instanceId;
            this.playerId = playerId;
            this.location = location;
        }
    }
}
