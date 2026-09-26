package id.cadera.cdrcarpenter;

import id.cadera.cdrcarpenter.command.CarpenterCommand;
import id.cadera.cdrcarpenter.furniture.FurnitureManager;
import id.cadera.cdrcarpenter.furniture.FurnitureRegistry;
import id.cadera.cdrcarpenter.listener.FurnitureListener;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("furniture.yml", false);

        furnitureItemKey = new NamespacedKey(this, "furniture_item");
        furnitureIdKey = new NamespacedKey(this, "furniture_id");
        instanceIdKey = new NamespacedKey(this, "instance_id");
        ownerKey = new NamespacedKey(this, "owner");

        furnitureRegistry = new FurnitureRegistry(this);
        furnitureRegistry.load();
        furnitureManager = new FurnitureManager(this, furnitureRegistry);

        getServer().getPluginManager().registerEvents(new FurnitureListener(this, furnitureManager, furnitureRegistry), this);

        CarpenterCommand command = new CarpenterCommand(this, furnitureRegistry, furnitureManager);
        PluginCommand pluginCommand = getCommand("carpenter");
        if (pluginCommand == null) {
            throw new IllegalStateException("Command 'carpenter' is missing from plugin.yml");
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        boolean gsit = getServer().getPluginManager().isPluginEnabled("GSit");
        getLogger().info("CdrCarpenter v" + getPluginMeta().getVersion() + " enabled.");
        getLogger().info("Loaded " + furnitureRegistry.size() + " furniture definitions.");
        getLogger().info("GSit: " + (gsit ? "detected (seat integration reserved for a later phase)" : "not detected"));
    }

    public void reloadPlugin() {
        reloadConfig();
        furnitureRegistry.load();
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
