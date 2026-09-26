package id.cadera.cdrcarpenter.furniture;

import id.cadera.cdrcarpenter.CdrCarpenter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FurnitureManager {
    private static final double BOX_EPSILON = 0.001D;

    private final CdrCarpenter plugin;
    private final FurnitureRegistry registry;
    private final Set<UUID> collisionBypass = ConcurrentHashMap.newKeySet();

    public FurnitureManager(CdrCarpenter plugin, FurnitureRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public boolean place(Player player, FurnitureDefinition definition, Location base, EquipmentSlot hand) {
        float yaw = snapYaw(player.getLocation().getYaw());

        Location anchorLocation = base.clone().add(0.5D, 0.0D, 0.5D);
        anchorLocation.setYaw(yaw);

        Location interactionLocation = applyLocalOffset(
                anchorLocation,
                definition.hitboxOffsetX(),
                definition.hitboxOffsetY(),
                definition.hitboxOffsetZ(),
                yaw
        );
        interactionLocation.setYaw(yaw);

        if (definition.collisionMode() == FurnitureCollisionMode.BARRIER && !base.getBlock().isPassable()) {
            player.sendMessage("§cThere is not enough room to place furniture there.");
            return false;
        }

        if (definition.collisionMode() == FurnitureCollisionMode.CUSTOM
                && !canPlaceCustomCollision(interactionLocation, definition, yaw)) {
            player.sendMessage("§cThere is not enough room for this furniture footprint.");
            return false;
        }

        Location displayLocation = applyVisualOffset(anchorLocation, definition, yaw);
        displayLocation.setYaw(yaw);

        String instanceId = UUID.randomUUID().toString();
        String ownerId = player.getUniqueId().toString();

        ItemStack displayItem = FurnitureItemFactory.create(plugin, definition, 1);

        ItemDisplay display = displayLocation.getWorld().spawn(displayLocation, ItemDisplay.class, entity -> {
            entity.setPersistent(true);
            entity.setItemStack(displayItem);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
            entity.setRotation(yaw, 0.0F);
            tagEntity(entity, definition.id(), instanceId, ownerId);
        });

        Interaction interaction = interactionLocation.getWorld().spawn(interactionLocation, Interaction.class, entity -> {
            entity.setPersistent(true);
            entity.setInteractionWidth(definition.hitboxWidth());
            entity.setInteractionHeight(definition.hitboxHeight());
            entity.setResponsive(true);
            entity.setRotation(yaw, 0.0F);
            tagEntity(entity, definition.id(), instanceId, ownerId);
        });

        display.setPersistent(true);
        interaction.setPersistent(true);

        if (definition.collisionMode() == FurnitureCollisionMode.BARRIER) {
            base.getBlock().setType(Material.BARRIER, false);
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            consumeOne(player, hand);
        }

        if (plugin.getConfig().getBoolean("debug.log-placements", false)) {
            plugin.getLogger().info(player.getName() + " placed " + definition.id()
                    + " instance=" + instanceId
                    + " collision=" + definition.collisionMode().name()
                    + " visualOffset=(" + definition.visualOffsetX() + ", "
                    + definition.visualOffsetY() + ", "
                    + definition.visualOffsetZ() + ")"
                    + " hitboxOffset=(" + definition.hitboxOffsetX() + ", "
                    + definition.hitboxOffsetY() + ", "
                    + definition.hitboxOffsetZ() + ")");
        }
        return true;
    }

    public boolean pickup(Player player, Entity furnitureEntity) {
        if (!isFurnitureEntity(furnitureEntity)) {
            return false;
        }

        PersistentDataContainer pdc = furnitureEntity.getPersistentDataContainer();
        String furnitureId = pdc.get(plugin.furnitureIdKey(), PersistentDataType.STRING);
        String instanceId = pdc.get(plugin.instanceIdKey(), PersistentDataType.STRING);
        String ownerId = pdc.get(plugin.ownerKey(), PersistentDataType.STRING);
        if (furnitureId == null || instanceId == null) {
            return false;
        }

        if (plugin.getConfig().getBoolean("placement.owner-only-pickup", true)
                && !player.hasPermission("cdrcarpenter.admin")
                && !player.getUniqueId().toString().equals(ownerId)) {
            player.sendMessage("§cOnly the furniture owner can pick this up.");
            return true;
        }

        FurnitureDefinition definition = registry.get(furnitureId);
        if (definition == null) {
            player.sendMessage("§cFurniture definition '" + furnitureId + "' is missing.");
            return true;
        }

        Location formerAnchor = removeInstance(furnitureEntity.getLocation(), instanceId);
        if (formerAnchor != null && formerAnchor.getBlock().getType() == Material.BARRIER) {
            formerAnchor.getBlock().setType(Material.AIR, false);
        }

        ItemStack item = FurnitureItemFactory.create(plugin, definition, 1);
        var leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.sendMessage("§aPicked up §f" + definition.displayName() + "§a.");
        return true;
    }

    public Interaction findFurnitureAtBarrier(Location blockLocation) {
        Location center = blockLocation.clone().add(0.5D, 0.5D, 0.5D);
        Interaction nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Entity nearby : center.getWorld().getNearbyEntities(center, 0.8D, 1.5D, 0.8D)) {
            if (!(nearby instanceof Interaction interaction) || !isFurnitureEntity(interaction)) {
                continue;
            }
            double distance = interaction.getLocation().distanceSquared(center);
            if (distance < nearestDistance) {
                nearest = interaction;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public FurnitureDefinition definitionFor(Entity entity) {
        if (!isFurnitureEntity(entity)) {
            return null;
        }
        String id = entity.getPersistentDataContainer().get(plugin.furnitureIdKey(), PersistentDataType.STRING);
        return registry.get(id);
    }

    public BoundingBox customCollisionBox(Interaction interaction, FurnitureDefinition definition) {
        return customCollisionBox(interaction.getLocation(), definition, interaction.getLocation().getYaw());
    }

    private BoundingBox customCollisionBox(Location interactionLocation, FurnitureDefinition definition, float yaw) {
        Location center = applyLocalOffset(
                interactionLocation,
                definition.collisionOffsetX(),
                definition.collisionOffsetY(),
                definition.collisionOffsetZ(),
                yaw
        );

        int quarterTurns = Math.floorMod(Math.round(yaw / 90.0F), 4);
        double width = definition.collisionWidth();
        double depth = definition.collisionDepth();
        if ((quarterTurns & 1) == 1) {
            double tmp = width;
            width = depth;
            depth = tmp;
        }

        return new BoundingBox(
                center.getX() - (width / 2.0D),
                center.getY(),
                center.getZ() - (depth / 2.0D),
                center.getX() + (width / 2.0D),
                center.getY() + definition.collisionHeight(),
                center.getZ() + (depth / 2.0D)
        );
    }

    private boolean canPlaceCustomCollision(Location interactionLocation, FurnitureDefinition definition, float yaw) {
        BoundingBox proposed = customCollisionBox(interactionLocation, definition, yaw);
        World world = interactionLocation.getWorld();
        if (world == null) {
            return false;
        }

        int minX = (int) Math.floor(proposed.getMinX() + BOX_EPSILON);
        int maxX = (int) Math.floor(proposed.getMaxX() - BOX_EPSILON);
        int minY = (int) Math.floor(proposed.getMinY() + BOX_EPSILON);
        int maxY = (int) Math.floor(proposed.getMaxY() - BOX_EPSILON);
        int minZ = (int) Math.floor(proposed.getMinZ() + BOX_EPSILON);
        int maxZ = (int) Math.floor(proposed.getMaxZ() - BOX_EPSILON);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!block.isPassable()) {
                        return false;
                    }
                }
            }
        }

        double radiusX = Math.max(2.5D, definition.collisionWidth() + 1.0D);
        double radiusY = Math.max(2.5D, definition.collisionHeight() + 1.0D);
        double radiusZ = Math.max(2.5D, definition.collisionDepth() + 1.0D);
        for (Entity nearby : world.getNearbyEntities(interactionLocation, radiusX, radiusY, radiusZ)) {
            if (!(nearby instanceof Interaction interaction) || !isFurnitureEntity(interaction)) {
                continue;
            }
            FurnitureDefinition other = definitionFor(interaction);
            if (other == null || other.collisionMode() != FurnitureCollisionMode.CUSTOM) {
                continue;
            }
            if (overlaps(proposed, customCollisionBox(interaction, other))) {
                return false;
            }
        }

        return true;
    }

    private boolean overlaps(BoundingBox a, BoundingBox b) {
        return a.getMaxX() > b.getMinX() + BOX_EPSILON
                && a.getMinX() < b.getMaxX() - BOX_EPSILON
                && a.getMaxY() > b.getMinY() + BOX_EPSILON
                && a.getMinY() < b.getMaxY() - BOX_EPSILON
                && a.getMaxZ() > b.getMinZ() + BOX_EPSILON
                && a.getMinZ() < b.getMaxZ() - BOX_EPSILON;
    }

    public void reconcileCollisions() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Interaction interaction : world.getEntitiesByClass(Interaction.class)) {
                if (!isFurnitureEntity(interaction)) {
                    continue;
                }
                FurnitureDefinition definition = definitionFor(interaction);
                if (definition == null) {
                    continue;
                }

                var block = interaction.getLocation().getBlock();
                if (definition.collisionMode() == FurnitureCollisionMode.BARRIER) {
                    if (block.getType() != Material.BARRIER && block.isPassable()) {
                        block.setType(Material.BARRIER, false);
                    }
                } else if (block.getType() == Material.BARRIER) {
                    block.setType(Material.AIR, false);
                }

                interaction.setInteractionWidth(definition.hitboxWidth());
                interaction.setInteractionHeight(definition.hitboxHeight());
            }
        }
    }

    public boolean isFurnitureEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(plugin.instanceIdKey(), PersistentDataType.STRING)
                && entity.getPersistentDataContainer().has(plugin.furnitureIdKey(), PersistentDataType.STRING);
    }

    public boolean isCollisionBypassed(Player player) {
        return collisionBypass.contains(player.getUniqueId());
    }

    public void setCollisionBypass(Player player, boolean bypass) {
        if (bypass) {
            collisionBypass.add(player.getUniqueId());
        } else {
            collisionBypass.remove(player.getUniqueId());
        }
    }

    private void tagEntity(Entity entity, String furnitureId, String instanceId, String ownerId) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(plugin.furnitureIdKey(), PersistentDataType.STRING, furnitureId);
        pdc.set(plugin.instanceIdKey(), PersistentDataType.STRING, instanceId);
        pdc.set(plugin.ownerKey(), PersistentDataType.STRING, ownerId);
    }

    private Location removeInstance(Location center, String instanceId) {
        Location interactionBlock = null;
        for (Entity nearby : center.getWorld().getNearbyEntities(center, 4.0D, 3.0D, 4.0D)) {
            String candidate = nearby.getPersistentDataContainer().get(plugin.instanceIdKey(), PersistentDataType.STRING);
            if (!instanceId.equals(candidate)) {
                continue;
            }
            if (nearby instanceof Interaction) {
                interactionBlock = nearby.getLocation().getBlock().getLocation();
            }
            nearby.remove();
        }
        return interactionBlock;
    }

    private Location applyVisualOffset(Location anchor, FurnitureDefinition definition, float yaw) {
        return applyLocalOffset(anchor, definition.visualOffsetX(), definition.visualOffsetY(), definition.visualOffsetZ(), yaw);
    }

    private Location applyLocalOffset(Location anchor, double localX, double localY, double localZ, float yaw) {
        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        double worldX = (localX * cos) - (localZ * sin);
        double worldZ = (localX * sin) + (localZ * cos);

        return anchor.clone().add(worldX, localY, worldZ);
    }

    private float snapYaw(float yaw) {
        int snap = Math.max(1, plugin.getConfig().getInt("placement.snap-rotation-degrees", 90));
        return Math.round(yaw / snap) * snap;
    }

    private void consumeOne(Player player, EquipmentSlot hand) {
        ItemStack held = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();

        if (held.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            held.setAmount(held.getAmount() - 1);
        }
    }
}
