package id.cadera.cdrcarpenter;

import id.cadera.cdrcarpenter.command.CarpenterCommand;
import id.cadera.cdrcarpenter.furniture.FurnitureManager;
import id.cadera.cdrcarpenter.furniture.FurnitureRegistry;
import id.cadera.cdrcarpenter.integration.GSitBridge;
import id.cadera.cdrcarpenter.integration.ItemsAdderBridge;
import id.cadera.cdrcarpenter.listener.CustomCollisionListener;
import id.cadera.cdrcarpenter.listener.FurnitureListener;
import id.cadera.cdrcarpenter.seating.SeatingManager;
import id.cadera.cdrcarpenter.storage.StorageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CdrCarpenter extends JavaPlugin {
    private NamespacedKey furnitureItemKey;
    private NamespacedKey furnitureIdKey;
    private NamespacedKey instanceIdKey;
    private NamespacedKey ownerKey;

    private FurnitureRegistry furnitureRegistry;
    private FurnitureManager furnitureManager;
    private ItemsAdderBridge itemsAdderBridge;
    private GSitBridge gsitBridge;
    private SeatingManager seatingManager;
    private StorageManager storageManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("furniture.yml", false);

        furnitureItemKey = new NamespacedKey(this, "furniture_item");
        furnitureIdKey = new NamespacedKey(this, "furniture_id");
        instanceIdKey = new NamespacedKey(this, "instance_id");
        ownerKey = new NamespacedKey(this, "owner");

        itemsAdderBridge = new ItemsAdderBridge(this);
        furnitureRegistry = new FurnitureRegistry(this);
        furnitureRegistry.load();
        furnitureManager = new FurnitureManager(this, furnitureRegistry);
        gsitBridge = new GSitBridge(this);
        seatingManager = new SeatingManager(this, furnitureManager, gsitBridge);
        storageManager = new StorageManager(this, furnitureManager, furnitureRegistry);

        getServer().getPluginManager().registerEvents(
                new FurnitureListener(this, furnitureManager, furnitureRegistry, seatingManager, storageManager),
                this
        );
        getServer().getPluginManager().registerEvents(new CustomCollisionListener(furnitureManager), this);
        getServer().getPluginManager().registerEvents(seatingManager, this);
        getServer().getPluginManager().registerEvents(storageManager, this);
        seatingManager.start();

        CarpenterCommand command = new CarpenterCommand(this, furnitureRegistry, furnitureManager);
        PluginCommand pluginCommand = getCommand("carpenter");
        if (pluginCommand == null) {
            throw new IllegalStateException("Command 'carpenter' is missing from plugin.yml");
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        getServer().getScheduler().runTask(this, furnitureManager::reconcileCollisions);

        getLogger().info("CdrCarpenter v" + getPluginMeta().getVersion() + " enabled.");
        getLogger().info("Loaded " + furnitureRegistry.size() + " furniture definitions.");
        getLogger().info("ItemsAdder: " + (itemsAdderBridge.isAvailable() ? "detected - custom item rendering enabled" : "not detected - vanilla fallback enabled"));
        getLogger().info("GSit: " + gsitBridge.statusDescription());
        getLogger().info("Furniture storage: enabled - persistent storage.yml backend");
    }

    @Override
    public void onDisable() {
        if (seatingManager != null) {
            seatingManager.shutdown();
        }
        if (storageManager != null) {
            storageManager.shutdown();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        if (storageManager != null) {
            storageManager.reload();
        }
        furnitureRegistry.load();
        furnitureManager.reconcileCollisions();
    }

    public ItemsAdderBridge itemsAdderBridge() {
        return itemsAdderBridge;
    }

    public NamespacedKey furnitureItemKey() {
        return furnitureItemKey;
    }

    public NamespacedKey furnitureIdKey() {
        return furnitureIdKey;
    }

    public NamespacedKey instanceIdKey() {
        return instanceIdKey;
    }

    public NamespacedKey ownerKey() {
        return ownerKey;
    }
}
