package id.cadera.cdrcarpenter.furniture;

import id.cadera.cdrcarpenter.CdrCarpenter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
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

        boolean migrated = false;

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

            float hitboxWidth = (float) section.getDouble("hitbox.width", 1.0D);
            float hitboxHeight = (float) section.getDouble("hitbox.height", 1.0D);
            hitboxWidth = Math.max(0.1F, Math.min(hitboxWidth, 4.0F));
            hitboxHeight = Math.max(0.1F, Math.min(hitboxHeight, 4.0F));

            double visualOffsetX = section.getDouble("visual-offset.x", 0.0D);
            double defaultVisualOffsetY = id.equals("chair") ? 0.55D : 0.0D;
            double visualOffsetY = section.getDouble("visual-offset.y", defaultVisualOffsetY);
            double visualOffsetZ = section.getDouble("visual-offset.z", 0.0D);

            FurnitureCollisionMode defaultMode;
            if (id.equals("chair")) {
                defaultMode = FurnitureCollisionMode.CUSTOM;
            } else if (section.getBoolean("collision.barrier", false)) {
                defaultMode = FurnitureCollisionMode.BARRIER;
            } else {
                defaultMode = FurnitureCollisionMode.NONE;
            }

            String rawMode = section.getString("collision.mode");
            FurnitureCollisionMode collisionMode = FurnitureCollisionMode.parse(rawMode, defaultMode);
            if (rawMode != null && !rawMode.equalsIgnoreCase(collisionMode.name())) {
                plugin.getLogger().warning("Furniture '" + id + "' has invalid collision.mode '" + rawMode
                        + "'. Using " + collisionMode.name() + ".");
            }

            double defaultCollisionWidth = id.equals("chair") ? 0.85D : 1.0D;
            double defaultCollisionDepth = id.equals("chair") ? 0.85D : 1.0D;
            double defaultCollisionHeight = id.equals("chair") ? 0.58D : 1.0D;

            double collisionWidth = clamp(section.getDouble("collision.width", defaultCollisionWidth), 0.10D, 4.0D);
            double collisionDepth = clamp(section.getDouble("collision.depth", defaultCollisionDepth), 0.10D, 4.0D);
            double collisionHeight = clamp(section.getDouble("collision.height", defaultCollisionHeight), 0.05D, 4.0D);
            double collisionOffsetX = section.getDouble("collision.offset.x", 0.0D);
            double collisionOffsetY = section.getDouble("collision.offset.y", 0.0D);
            double collisionOffsetZ = section.getDouble("collision.offset.z", 0.0D);

            if (!section.isSet("collision.mode")) {
                section.set("collision.mode", collisionMode.name());
                migrated = true;
            }
            if (section.isSet("collision.barrier")) {
                section.set("collision.barrier", null);
                migrated = true;
            }
            if (collisionMode == FurnitureCollisionMode.CUSTOM) {
                if (!section.isSet("collision.width")) {
                    section.set("collision.width", collisionWidth);
                    migrated = true;
                }
                if (!section.isSet("collision.depth")) {
                    section.set("collision.depth", collisionDepth);
                    migrated = true;
                }
                if (!section.isSet("collision.height")) {
                    section.set("collision.height", collisionHeight);
                    migrated = true;
                }
                if (!section.isSet("collision.offset.x")) {
                    section.set("collision.offset.x", collisionOffsetX);
                    migrated = true;
                }
                if (!section.isSet("collision.offset.y")) {
                    section.set("collision.offset.y", collisionOffsetY);
                    migrated = true;
                }
                if (!section.isSet("collision.offset.z")) {
                    section.set("collision.offset.z", collisionOffsetZ);
                    migrated = true;
                }
            }

            definitions.put(id, new FurnitureDefinition(
                    id,
                    displayName,
                    material,
                    customModelData,
                    itemsAdderId,
                    hitboxWidth,
                    hitboxHeight,
                    visualOffsetX,
                    visualOffsetY,
                    visualOffsetZ,
                    collisionMode,
                    collisionWidth,
                    collisionDepth,
                    collisionHeight,
                    collisionOffsetX,
                    collisionOffsetY,
                    collisionOffsetZ
            ));
        }

        if (migrated) {
            try {
                yaml.save(file);
                plugin.getLogger().info("Migrated furniture.yml to the v0.1.3 collision format.");
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save migrated furniture.yml: " + ex.getMessage());
            }
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

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}
