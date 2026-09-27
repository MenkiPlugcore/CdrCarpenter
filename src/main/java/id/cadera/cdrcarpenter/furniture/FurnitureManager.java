package id.cadera.cdrcarpenter.furniture;

import id.cadera.cdrcarpenter.CdrCarpenter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
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

        if (definition.collisionMode() == FurnitureCollisionMode.BARRIER
                && !canPlaceBarrierFootprint(interactionLocation, definition, yaw)) {
            player.sendMessage("§cThere is not enough room for this furniture footprint.");
            return false;
        }

        if (definition.collisionMode() == FurnitureCollisionMode.CUSTOM
                && !canPlaceCustomCollision(interactionLocation, definition, yaw)) {
            player.sendMessage("§cThere is not enough room for this furniture footprint.");
            return false;
        }

        if (definition.collisionMode() == FurnitureCollisionMode.BLOCK) {
            if (definition.collisionBlocks().isEmpty()) {
                player.sendMessage("§cThis furniture has BLOCK collision enabled but no valid collision block is configured.");
                return false;
            }
            if (!canPlaceBlockCollision(interactionLocation, definition, yaw)) {
                player.sendMessage("§cThere is not enough room for this furniture collision block.");
                return false;
            }
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
            placeBarrierFootprint(interaction.getLocation(), definition);
        } else if (definition.collisionMode() == FurnitureCollisionMode.BLOCK) {
            placeBlockCollision(interaction.getLocation(), definition);
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

        Location formerInteraction = removeInstance(furnitureEntity.getLocation(), instanceId);
        if (formerInteraction != null) {
            if (definition.collisionMode() == FurnitureCollisionMode.BLOCK) {
                clearBlockCollision(formerInteraction, definition);
            } else {
                clearBarrierFootprint(formerInteraction, definition);
                // Upgrade-safe cleanup for pre-footprint single-barrier versions.
                if (formerInteraction.getBlock().getType() == Material.BARRIER) {
                    formerInteraction.getBlock().setType(Material.AIR, false);
                }
            }
        }

        ItemStack item = FurnitureItemFactory.create(plugin, definition, 1);
        var leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.sendMessage("§aPicked up §f" + definition.displayName() + "§a.");
        return true;
    }

    public Interaction findFurnitureAtBarrier(Location blockLocation) {
        return findFurnitureAtCollisionBlock(blockLocation);
    }

    public Interaction findFurnitureAtCollisionBlock(Location blockLocation) {
        Block clicked = blockLocation.getBlock();
        Location center = blockLocation.clone().add(0.5D, 0.5D, 0.5D);
        Interaction fallback = null;
        double fallbackDistance = Double.MAX_VALUE;

        for (Entity nearby : center.getWorld().getNearbyEntities(center, 5.5D, 3.5D, 5.5D)) {
            if (!(nearby instanceof Interaction interaction) || !isFurnitureEntity(interaction)) {
                continue;
            }

            FurnitureDefinition definition = definitionFor(interaction);
            if (definition == null) {
                continue;
            }

            if (definition.collisionMode() == FurnitureCollisionMode.BARRIER) {
                for (Block footprint : barrierFootprint(interaction.getLocation(), definition, interaction.getLocation().getYaw())) {
                    if (sameBlock(footprint, clicked)) {
                        return interaction;
                    }
                }
            } else if (definition.collisionMode() == FurnitureCollisionMode.BLOCK) {
                for (CollisionTarget target : blockCollisionTargets(
                        interaction.getLocation(), definition, interaction.getLocation().getYaw())) {
                    if (sameBlock(target.block(), clicked) && clicked.getType() == target.definition().material()) {
                        return interaction;
                    }
                }
            }

            // Legacy fallback for old single-barrier furniture only.
            if (clicked.getType() == Material.BARRIER) {
                double distance = interaction.getLocation().distanceSquared(center);
                if (distance < fallbackDistance && distance <= 2.25D) {
                    fallback = interaction;
                    fallbackDistance = distance;
                }
            }
        }
        return fallback;
    }

    public boolean isConfiguredCollisionMaterial(Material material) {
        if (material == Material.BARRIER) {
            return true;
        }
        for (FurnitureDefinition definition : registry.all()) {
            if (definition.collisionMode() != FurnitureCollisionMode.BLOCK) {
                continue;
            }
            for (FurnitureCollisionBlock collisionBlock : definition.collisionBlocks()) {
                if (collisionBlock.material() == material) {
                    return true;
                }
            }
        }
        return false;
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

        return !overlapsExistingCustomFurniture(proposed, interactionLocation, definition);
    }

    private boolean canPlaceBarrierFootprint(Location interactionLocation, FurnitureDefinition definition, float yaw) {
        World world = interactionLocation.getWorld();
        if (world == null) {
            return false;
        }

        for (Block block : barrierFootprint(interactionLocation, definition, yaw)) {
            if (!block.getType().isAir()) {
                return false;
            }
        }

        BoundingBox proposed = customCollisionBox(interactionLocation, definition, yaw);
        return !overlapsExistingCustomFurniture(proposed, interactionLocation, definition);
    }

    private boolean canPlaceBlockCollision(Location interactionLocation, FurnitureDefinition definition, float yaw) {
        if (definition.collisionBlocks().isEmpty() || interactionLocation.getWorld() == null) {
            return false;
        }
        for (CollisionTarget target : blockCollisionTargets(interactionLocation, definition, yaw)) {
            if (!target.block().getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private boolean overlapsExistingCustomFurniture(
            BoundingBox proposed,
            Location interactionLocation,
            FurnitureDefinition definition
    ) {
        World world = interactionLocation.getWorld();
        if (world == null) {
            return true;
        }

        double radiusX = Math.max(3.5D, definition.collisionWidth() + 1.5D);
        double radiusY = Math.max(2.5D, definition.collisionHeight() + 1.0D);
        double radiusZ = Math.max(3.5D, definition.collisionDepth() + 1.5D);
        for (Entity nearby : world.getNearbyEntities(interactionLocation, radiusX, radiusY, radiusZ)) {
            if (!(nearby instanceof Interaction interaction) || !isFurnitureEntity(interaction)) {
                continue;
            }
            FurnitureDefinition other = definitionFor(interaction);
            if (other == null || other.collisionMode() != FurnitureCollisionMode.CUSTOM) {
                continue;
            }
            if (overlaps(proposed, customCollisionBox(interaction, other))) {
                return true;
            }
        }
        return false;
    }

    private List<Block> barrierFootprint(Location interactionLocation, FurnitureDefinition definition, float yaw) {
        List<Block> blocks = new ArrayList<>();
        World world = interactionLocation.getWorld();
        if (world == null) {
            return blocks;
        }

        BoundingBox footprint = customCollisionBox(interactionLocation, definition, yaw);
        int minX = (int) Math.floor(footprint.getMinX() + BOX_EPSILON);
        int maxX = (int) Math.floor(footprint.getMaxX() - BOX_EPSILON);
        int y = (int) Math.floor(footprint.getMinY() + BOX_EPSILON);
        int minZ = (int) Math.floor(footprint.getMinZ() + BOX_EPSILON);
        int maxZ = (int) Math.floor(footprint.getMaxZ() - BOX_EPSILON);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                blocks.add(world.getBlockAt(x, y, z));
            }
        }
        return blocks;
    }

    private List<CollisionTarget> blockCollisionTargets(
            Location interactionLocation,
            FurnitureDefinition definition,
            float yaw
    ) {
        List<CollisionTarget> targets = new ArrayList<>();
        Block anchor = interactionLocation.getBlock();
        int quarterTurns = Math.floorMod(Math.round(yaw / 90.0F), 4);

        for (FurnitureCollisionBlock collisionBlock : definition.collisionBlocks()) {
            int x = collisionBlock.offsetX();
            int z = collisionBlock.offsetZ();
            int worldX;
            int worldZ;
            switch (quarterTurns) {
                case 1 -> {
                    worldX = -z;
                    worldZ = x;
                }
                case 2 -> {
                    worldX = -x;
                    worldZ = -z;
                }
                case 3 -> {
                    worldX = z;
                    worldZ = -x;
                }
                default -> {
                    worldX = x;
                    worldZ = z;
                }
            }
            targets.add(new CollisionTarget(
                    anchor.getRelative(worldX, collisionBlock.offsetY(), worldZ),
                    collisionBlock
            ));
        }
        return targets;
    }

    private void placeBarrierFootprint(Location interactionLocation, FurnitureDefinition definition) {
        for (Block block : barrierFootprint(interactionLocation, definition, interactionLocation.getYaw())) {
            if (block.getType().isAir()) {
                block.setType(Material.BARRIER, false);
            }
        }
    }

    private void clearBarrierFootprint(Location interactionLocation, FurnitureDefinition definition) {
        for (Block block : barrierFootprint(interactionLocation, definition, interactionLocation.getYaw())) {
            if (block.getType() == Material.BARRIER) {
                block.setType(Material.AIR, false);
            }
        }
    }

    private void placeBlockCollision(Location interactionLocation, FurnitureDefinition definition) {
        float yaw = interactionLocation.getYaw();
        for (CollisionTarget target : blockCollisionTargets(interactionLocation, definition, yaw)) {
            Block block = target.block();
            if (!block.getType().isAir() && block.getType() != target.definition().material()) {
                continue;
            }
            if (block.getType().isAir()) {
                block.setType(target.definition().material(), false);
            }
            orientCollisionBlock(block, yaw);
        }
    }

    private void clearBlockCollision(Location interactionLocation, FurnitureDefinition definition) {
        for (CollisionTarget target : blockCollisionTargets(
                interactionLocation, definition, interactionLocation.getYaw())) {
            if (target.block().getType() == target.definition().material()) {
                target.block().setType(Material.AIR, false);
            }
        }
    }

    private void orientCollisionBlock(Block block, float yaw) {
        BlockFace face = faceForYaw(yaw);
        BlockData data = block.getBlockData();
        boolean changed = false;

        if (data instanceof Directional directional && directional.getFaces().contains(face)) {
            directional.setFacing(face);
            changed = true;
        }
        if (data instanceof Rotatable rotatable) {
            rotatable.setRotation(face);
            changed = true;
        }
        if (changed) {
            block.setBlockData(data, false);
        }
    }

    private BlockFace faceForYaw(float yaw) {
        int quarterTurns = Math.floorMod(Math.round(yaw / 90.0F), 4);
        return switch (quarterTurns) {
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            case 3 -> BlockFace.EAST;
            default -> BlockFace.SOUTH;
        };
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

                if (definition.collisionMode() == FurnitureCollisionMode.BARRIER) {
                    placeBarrierFootprint(interaction.getLocation(), definition);
                } else if (definition.collisionMode() == FurnitureCollisionMode.BLOCK) {
                    // Remove a legacy single barrier if this furniture was upgraded to BLOCK mode.
                    if (interaction.getLocation().getBlock().getType() == Material.BARRIER) {
                        interaction.getLocation().getBlock().setType(Material.AIR, false);
                    }
                    placeBlockCollision(interaction.getLocation(), definition);
                } else {
                    // Clears barriers when a furniture definition is migrated away from BARRIER.
                    clearBarrierFootprint(interaction.getLocation(), definition);
                    if (interaction.getLocation().getBlock().getType() == Material.BARRIER) {
                        interaction.getLocation().getBlock().setType(Material.AIR, false);
                    }
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
        Location interactionLocation = null;
        for (Entity nearby : center.getWorld().getNearbyEntities(center, 4.0D, 3.0D, 4.0D)) {
            String candidate = nearby.getPersistentDataContainer().get(plugin.instanceIdKey(), PersistentDataType.STRING);
            if (!instanceId.equals(candidate)) {
                continue;
            }
            if (nearby instanceof Interaction) {
                interactionLocation = nearby.getLocation().clone();
            }
            nearby.remove();
        }
        return interactionLocation;
    }

    private boolean sameBlock(Block first, Block second) {
        return first.getWorld().equals(second.getWorld())
                && first.getX() == second.getX()
                && first.getY() == second.getY()
                && first.getZ() == second.getZ();
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

    private record CollisionTarget(Block block, FurnitureCollisionBlock definition) {
    }
}
