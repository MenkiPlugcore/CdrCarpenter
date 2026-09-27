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
    private static final int CONFIG_VERSION = 4;

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

        boolean migrated = migrateConfig(yaml, root);

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

            double defaultHitboxWidth = id.equals("table") ? 2.0D : 1.0D;
            double defaultHitboxHeight = id.equals("table") ? 1.10D : 1.0D;
            float hitboxWidth = (float) section.getDouble("hitbox.width", defaultHitboxWidth);
            float hitboxHeight = (float) section.getDouble("hitbox.height", defaultHitboxHeight);
            hitboxWidth = Math.max(0.1F, Math.min(hitboxWidth, 4.0F));
            hitboxHeight = Math.max(0.1F, Math.min(hitboxHeight, 4.0F));

            double defaultHitboxOffsetX = id.equals("table") ? 0.50D : 0.0D;
            double defaultHitboxOffsetZ = id.equals("table") ? -0.50D : 0.0D;
            double hitboxOffsetX = section.getDouble("hitbox.offset.x", defaultHitboxOffsetX);
            double hitboxOffsetY = section.getDouble("hitbox.offset.y", 0.0D);
            double hitboxOffsetZ = section.getDouble("hitbox.offset.z", defaultHitboxOffsetZ);

            double visualOffsetX = section.getDouble("visual-offset.x", 0.0D);
            double defaultVisualOffsetY = id.equals("chair") ? 0.55D : (id.equals("table") ? 0.50D : 0.0D);
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

            double defaultCollisionWidth = id.equals("chair") ? 0.85D : (id.equals("table") ? 2.0D : 1.0D);
            double defaultCollisionDepth = id.equals("chair") ? 0.85D : (id.equals("table") ? 2.0D : 1.0D);
            double defaultCollisionHeight = id.equals("chair") ? 0.58D : 1.0D;

            double collisionWidth = clamp(section.getDouble("collision.width", defaultCollisionWidth), 0.10D, 4.0D);
            double collisionDepth = clamp(section.getDouble("collision.depth", defaultCollisionDepth), 0.10D, 4.0D);
            double collisionHeight = clamp(section.getDouble("collision.height", defaultCollisionHeight), 0.05D, 4.0D);
            double collisionOffsetX = section.getDouble("collision.offset.x", 0.0D);
            double collisionOffsetY = section.getDouble("collision.offset.y", 0.0D);
            double collisionOffsetZ = section.getDouble("collision.offset.z", 0.0D);
            boolean collisionStandable = section.getBoolean("collision.standable", !id.equals("chair"));

            boolean seatEnabled = section.getBoolean("seat.enabled", id.equals("chair"));
            double seatOffsetX = section.getDouble("seat.offset.x", 0.0D);
            double seatOffsetY = section.getDouble("seat.offset.y", id.equals("chair") ? 0.55D : 0.0D);
            double seatOffsetZ = section.getDouble("seat.offset.z", 0.0D);
            float seatYawOffset = (float) section.getDouble("seat.yaw-offset", 0.0D);
            boolean seatCanRotate = section.getBoolean("seat.can-rotate", false);

            if (!section.isSet("collision.mode")) {
                section.set("collision.mode", collisionMode.name());
                migrated = true;
            }
            if (section.isSet("collision.barrier")) {
                section.set("collision.barrier", null);
                migrated = true;
            }
            if (collisionMode == FurnitureCollisionMode.CUSTOM || collisionMode == FurnitureCollisionMode.BARRIER) {
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
                if (!section.isSet("collision.standable")) {
                    section.set("collision.standable", collisionStandable);
                    migrated = true;
                }
            }

            if (!section.isSet("hitbox.offset.x")) {
                section.set("hitbox.offset.x", hitboxOffsetX);
                migrated = true;
            }
            if (!section.isSet("hitbox.offset.y")) {
                section.set("hitbox.offset.y", hitboxOffsetY);
                migrated = true;
            }
            if (!section.isSet("hitbox.offset.z")) {
                section.set("hitbox.offset.z", hitboxOffsetZ);
                migrated = true;
            }

            if (id.equals("chair") || section.isConfigurationSection("seat") || section.isSet("seat.enabled")) {
                if (!section.isSet("seat.enabled")) {
                    section.set("seat.enabled", seatEnabled);
                    migrated = true;
                }
                if (!section.isSet("seat.offset.x")) {
                    section.set("seat.offset.x", seatOffsetX);
                    migrated = true;
                }
                if (!section.isSet("seat.offset.y")) {
                    section.set("seat.offset.y", seatOffsetY);
                    migrated = true;
                }
                if (!section.isSet("seat.offset.z")) {
                    section.set("seat.offset.z", seatOffsetZ);
                    migrated = true;
                }
                if (!section.isSet("seat.yaw-offset")) {
                    section.set("seat.yaw-offset", seatYawOffset);
                    migrated = true;
                }
                if (!section.isSet("seat.can-rotate")) {
                    section.set("seat.can-rotate", seatCanRotate);
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
                    hitboxOffsetX,
                    hitboxOffsetY,
                    hitboxOffsetZ,
                    visualOffsetX,
                    visualOffsetY,
                    visualOffsetZ,
                    collisionMode,
                    collisionWidth,
                    collisionDepth,
                    collisionHeight,
                    collisionOffsetX,
                    collisionOffsetY,
                    collisionOffsetZ,
                    collisionStandable,
                    seatEnabled,
                    seatOffsetX,
                    seatOffsetY,
                    seatOffsetZ,
                    seatYawOffset,
                    seatCanRotate
            ));
        }

        if (migrated) {
            try {
                yaml.save(file);
                plugin.getLogger().info("Migrated furniture.yml to config version " + CONFIG_VERSION + ".");
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save migrated furniture.yml: " + ex.getMessage());
            }
        }
    }

    private boolean migrateConfig(YamlConfiguration yaml, ConfigurationSection root) {
        int currentVersion = yaml.getInt("config-version", 1);
        if (currentVersion >= CONFIG_VERSION) {
            return false;
        }

        boolean changed = false;

        if (currentVersion < 2) {
            ConfigurationSection table = root.getConfigurationSection("table");
            if (table != null) {
                table.set("display-name", "Comfy Dinner Table");
                table.set("itemsadder-id", "cdrcarpenter:table");
                table.set("visual-offset.x", 0.0D);
                table.set("visual-offset.y", 0.50D);
                table.set("visual-offset.z", 0.0D);
                table.set("collision.mode", "CUSTOM");
                table.set("collision.width", 2.0D);
                table.set("collision.depth", 2.0D);
                table.set("collision.height", 1.0D);
                table.set("collision.standable", true);
                table.set("collision.offset.x", 0.0D);
                table.set("collision.offset.y", 0.0D);
                table.set("collision.offset.z", 0.0D);
                table.set("hitbox.width", 2.0D);
                table.set("hitbox.height", 1.10D);
                table.set("hitbox.offset.x", 0.50D);
                table.set("hitbox.offset.y", 0.0D);
                table.set("hitbox.offset.z", -0.50D);
                table.set("seat.enabled", false);
                changed = true;
            }
            currentVersion = 2;
            yaml.set("config-version", currentVersion);
            changed = true;
        }

        if (currentVersion < 3) {
            ConfigurationSection table = root.getConfigurationSection("table");
            if (table != null) {
                table.set("collision.mode", "BARRIER");
                table.set("collision.width", 2.0D);
                table.set("collision.depth", 2.0D);
                table.set("collision.height", 1.0D);
                table.set("collision.standable", true);
                table.set("collision.offset.x", 0.0D);
                table.set("collision.offset.y", 0.0D);
                table.set("collision.offset.z", 0.0D);
                changed = true;
            }
            currentVersion = 3;
            yaml.set("config-version", currentVersion);
            changed = true;
        }

        if (currentVersion < 4) {
            ConfigurationSection table = root.getConfigurationSection("table");
            if (table != null) {
                // Dinner Table is visual-only from v0.1.5-hotfix2 onward. The table model has
                // open space between its legs, and both custom movement correction and a full
                // 2x2 barrier footprint felt disconnected from the visual geometry.
                table.set("collision.mode", "NONE");
                changed = true;
            }
            currentVersion = 4;
            yaml.set("config-version", currentVersion);
            changed = true;
        }

        return changed;
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
