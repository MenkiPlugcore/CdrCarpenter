package id.cadera.cdrcarpenter;

import id.cadera.cdrcarpenter.blueprint.BlueprintManager;
import id.cadera.cdrcarpenter.command.CarpenterCommand;
import id.cadera.cdrcarpenter.crafting.SawmillManager;
import id.cadera.cdrcarpenter.crafting.WorkbenchManager;
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
    private NamespacedKey materialItemKey;
    private NamespacedKey blueprintIdKey;

    private FurnitureRegistry furnitureRegistry;
    private FurnitureManager furnitureManager;
    private ItemsAdderBridge itemsAdderBridge;
    private GSitBridge gsitBridge;
    private SeatingManager seatingManager;
    private StorageManager storageManager;
    private BlueprintManager blueprintManager;
    private WorkbenchManager workbenchManager;
    private SawmillManager sawmillManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("furniture.yml", false);
        saveResource("workbench.yml", false);
        saveResource("sawmill.yml", false);
        saveResource("blueprints.yml", false);

        furnitureItemKey = new NamespacedKey(this, "furniture_item");
        furnitureIdKey = new NamespacedKey(this, "furniture_id");
        instanceIdKey = new NamespacedKey(this, "instance_id");
        ownerKey = new NamespacedKey(this, "owner");
        materialItemKey = new NamespacedKey(this, "material_item");
        blueprintIdKey = new NamespacedKey(this, "blueprint_id");

        itemsAdderBridge = new ItemsAdderBridge(this);
        furnitureRegistry = new FurnitureRegistry(this);
        furnitureRegistry.load();
        furnitureManager = new FurnitureManager(this, furnitureRegistry);
        gsitBridge = new GSitBridge(this);
        seatingManager = new SeatingManager(this, furnitureManager, gsitBridge);
        storageManager = new StorageManager(this, furnitureManager, furnitureRegistry);
        blueprintManager = new BlueprintManager(this);
        workbenchManager = new WorkbenchManager(this, furnitureManager, furnitureRegistry, blueprintManager);
        sawmillManager = new SawmillManager(this, furnitureManager);

        getServer().getPluginManager().registerEvents(
                new FurnitureListener(
                        this,
                        furnitureManager,
                        furnitureRegistry,
                        seatingManager,
                        storageManager,
                        workbenchManager,
                        sawmillManager
                ),
                this
        );
        getServer().getPluginManager().registerEvents(new CustomCollisionListener(furnitureManager), this);
        getServer().getPluginManager().registerEvents(seatingManager, this);
        getServer().getPluginManager().registerEvents(storageManager, this);
        getServer().getPluginManager().registerEvents(blueprintManager, this);
        getServer().getPluginManager().registerEvents(workbenchManager, this);
        getServer().getPluginManager().registerEvents(sawmillManager, this);
        seatingManager.start();

        CarpenterCommand command = new CarpenterCommand(this, furnitureRegistry, furnitureManager, blueprintManager);
        PluginCommand pluginCommand = getCommand("carpenter");
        if (pluginCommand == null) {
            throw new IllegalStateException("Command 'carpenter' is missing from plugin.yml");
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        getServer().getScheduler().runTask(this, furnitureManager::reconcileCollisions);

        getLogger().info("CdrCarpenter v" + getPluginMeta().getVersion() + " enabled.");
        getLogger().info("Loaded " + furnitureRegistry.size() + " furniture definitions.");
        getLogger().info("Loaded " + blueprintManager.all().size() + " Carpenter Blueprints.");
        getLogger().info("ItemsAdder: " + (itemsAdderBridge.isAvailable() ? "detected - custom item rendering enabled" : "not detected - vanilla fallback enabled"));
        getLogger().info("GSit: " + gsitBridge.statusDescription());
        getLogger().info("Furniture storage: enabled - persistent storage.yml backend");
        getLogger().info("Carpenter Workbench: blueprint-gated crafting enabled");
        getLogger().info("Carpenter Sawmill: log processing enabled");
    }

    @Override
    public void onDisable() {
        if (sawmillManager != null) {
            sawmillManager.shutdown();
        }
        if (workbenchManager != null) {
            workbenchManager.shutdown();
        }
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
        if (blueprintManager != null) {
            blueprintManager.reload();
        }
        if (workbenchManager != null) {
            workbenchManager.reload();
        }
        if (sawmillManager != null) {
            sawmillManager.reload();
        }
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

    public NamespacedKey materialItemKey() {
        return materialItemKey;
    }

    public NamespacedKey blueprintIdKey() {
        return blueprintIdKey;
    }
}
