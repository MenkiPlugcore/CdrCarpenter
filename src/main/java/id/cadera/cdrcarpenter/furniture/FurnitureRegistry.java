package id.cadera.cdrcarpenter.furniture;

import id.cadera.cdrcarpenter.CdrCarpenter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class FurnitureRegistry {
    private final CdrCarpenter plugin;
    private final Map<String, FurnitureDefinition> definitions = new LinkedHashMap<>();

    public FurnitureRegistry(CdrCarpenter plugin) {
        this.plugin = plugin;
    }

    public void load() {
        definitions.clear();

        File file = new File(plugin.getDataFolder(), "furniture.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("furniture");
        if (root == null) {
            plugin.getLogger().warning("No 'furniture' section found in furniture.yml.");
            return;
        }

        for (String rawId : root.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }

            String displayName = section.getString("display-name", id);
            String materialName = section.getString("material", "PAPER");
            Material material = Material.matchMaterial(materialName);
            if (material == null || material.isAir()) {
                plugin.getLogger().warning("Skipping furniture '" + id + "': invalid material '" + materialName + "'.");
                continue;
            }

            int customModelData = section.getInt("custom-model-data", 0);
            String itemsAdderId = section.getString("itemsadder-id", "cdrcarpenter:" + id).trim();
            if (itemsAdderId.isEmpty()) {
                itemsAdderId = null;
            }

            float width = (float) section.getDouble("hitbox.width", 1.0D);
            float height = (float) section.getDouble("hitbox.height", 1.0D);
            width = Math.max(0.1F, Math.min(width, 4.0F));
            height = Math.max(0.1F, Math.min(height, 4.0F));

            definitions.put(id, new FurnitureDefinition(id, displayName, material, customModelData, itemsAdderId, width, height));
        }
    }

    public FurnitureDefinition get(String id) {
        if (id == null) {
            return null;
        }
        return definitions.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<FurnitureDefinition> all() {
        return definitions.values();
    }

    public int size() {
        return definitions.size();
    }
}
