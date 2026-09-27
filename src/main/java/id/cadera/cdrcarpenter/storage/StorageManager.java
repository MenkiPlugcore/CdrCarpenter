package id.cadera.cdrcarpenter.storage;

import id.cadera.cdrcarpenter.CdrCarpenter;
import id.cadera.cdrcarpenter.furniture.FurnitureDefinition;
import id.cadera.cdrcarpenter.furniture.FurnitureManager;
import id.cadera.cdrcarpenter.furniture.FurnitureRegistry;
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
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class StorageManager implements Listener {
    private final CdrCarpenter plugin;
    private final FurnitureManager furnitureManager;
    private final FurnitureRegistry registry;
    private final File storageFile;
    private final YamlConfiguration data;
    private final Map<String, Inventory> inventoriesByInstance = new HashMap<>();
    private final Map<Inventory, Session> sessions = new IdentityHashMap<>();

    public StorageManager(CdrCarpenter plugin, FurnitureManager furnitureManager, FurnitureRegistry registry) {
        this.plugin = plugin;
        this.furnitureManager = furnitureManager;
        this.registry = registry;
        this.storageFile = new File(plugin.getDataFolder(), "storage.yml");
        this.data = YamlConfiguration.loadConfiguration(storageFile);
    }

    public boolean open(Player player, Interaction interaction) {
        FurnitureDefinition definition = furnitureManager.definitionFor(interaction);
        if (definition == null || !definition.storageEnabled()) {
            return false;
        }

        String instanceId = instanceId(interaction);
        if (instanceId == null) {
            return false;
        }

        Inventory inventory = inventoriesByInstance.get(instanceId);
        if (inventory == null) {
            int size = Math.max(9, Math.min(definition.storageRows(), 6) * 9);
            inventory = Bukkit.createInventory(null, size, definition.storageTitle());
            loadInventory(instanceId, inventory);
            inventoriesByInstance.put(instanceId, inventory);
            sessions.put(inventory, new Session(instanceId, definition.id()));
        }

        player.openInventory(inventory);
        return true;
    }

    public String pickupBlockReason(Interaction interaction) {
        FurnitureDefinition definition = furnitureManager.definitionFor(interaction);
        if (definition == null || !definition.storageEnabled()) {
            return null;
        }

        String instanceId = instanceId(interaction);
        if (instanceId == null) {
            return null;
        }

        Inventory active = inventoriesByInstance.get(instanceId);
        if (active != null && !active.getViewers().isEmpty()) {
            return "§eSomeone is using this bookshelf storage.";
        }

        if (!isEmpty(instanceId)) {
            return "§eEmpty the bookshelf before picking it up.";
        }
        return null;
    }

    public void discardEmptyStorage(Interaction interaction) {
        String instanceId = instanceId(interaction);
        if (instanceId == null || !isEmpty(instanceId)) {
            return;
        }

        Inventory inventory = inventoriesByInstance.remove(instanceId);
        if (inventory != null) {
            sessions.remove(inventory);
        }
        data.set("storages." + instanceId, null);
        saveData();
    }

    public void shutdown() {
        saveAllOpenInventories();
    }

    public void reload() {
        saveAllOpenInventories();
        inventoriesByInstance.clear();
        sessions.clear();
    }

    private boolean isEmpty(String instanceId) {
        Inventory inventory = inventoriesByInstance.get(instanceId);
        if (inventory != null) {
            for (ItemStack item : inventory.getContents()) {
                if (item != null && !item.getType().isAir()) {
                    return false;
                }
            }
            return true;
        }

        ConfigurationSection items = data.getConfigurationSection("storages." + instanceId + ".items");
        if (items == null) {
            return true;
        }
        for (String key : items.getKeys(false)) {
            ItemStack item = items.getItemStack(key);
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private void loadInventory(String instanceId, Inventory inventory) {
        ConfigurationSection items = data.getConfigurationSection("storages." + instanceId + ".items");
        if (items == null) {
            return;
        }

        for (String key : items.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                if (slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }
                ItemStack item = items.getItemStack(key);
                if (item != null && !item.getType().isAir()) {
                    inventory.setItem(slot, item);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed legacy slot keys instead of breaking the whole storage.
            }
        }
    }

    private void saveInventory(String instanceId, Inventory inventory) {
        String base = "storages." + instanceId;
        data.set(base + ".items", null);
        data.set(base + ".size", inventory.getSize());

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            data.set(base + ".items." + slot, item);
        }
        saveData();
    }

    private void saveAllOpenInventories() {
        for (Map.Entry<Inventory, Session> entry : sessions.entrySet()) {
            saveInventory(entry.getValue().instanceId(), entry.getKey());
        }
    }

    private void saveData() {
        try {
            data.save(storageFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save storage.yml: " + exception.getMessage());
        }
    }

    private String instanceId(Interaction interaction) {
        return interaction.getPersistentDataContainer().get(plugin.instanceIdKey(), PersistentDataType.STRING);
    }

    private boolean allowed(FurnitureDefinition definition, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return true;
        }
        Set<Material> allowed = definition.storageAllowedMaterials();
        return allowed == null || allowed.isEmpty() || allowed.contains(item.getType());
    }

    private FurnitureDefinition definitionFor(Inventory inventory) {
        Session session = sessions.get(inventory);
        return session == null ? null : registry.get(session.furnitureId());
    }

    private void reject(Player player, InventoryClickEvent event) {
        event.setCancelled(true);
        player.sendMessage("§cThat item cannot be stored in this bookshelf.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        FurnitureDefinition definition = definitionFor(top);
        if (definition == null || !definition.storageEnabled()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.isShiftClick() && event.getClickedInventory() != top) {
            if (!allowed(definition, event.getCurrentItem())) {
                reject(player, event);
            }
            return;
        }

        if (event.getClickedInventory() == top) {
            if (!allowed(definition, event.getCursor())) {
                reject(player, event);
                return;
            }

            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0 && hotbarButton < player.getInventory().getSize()) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbarButton);
                if (!allowed(definition, hotbarItem)) {
                    reject(player, event);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        FurnitureDefinition definition = definitionFor(top);
        if (definition == null || !definition.storageEnabled()) {
            return;
        }

        boolean touchesStorage = event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize());
        if (!touchesStorage || allowed(definition, event.getOldCursor())) {
            return;
        }

        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            player.sendMessage("§cThat item cannot be stored in this bookshelf.");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Session session = sessions.get(event.getInventory());
        if (session == null) {
            return;
        }
        saveInventory(session.instanceId(), event.getInventory());
    }

    private record Session(String instanceId, String furnitureId) {
    }
}
