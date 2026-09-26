package id.cadera.cdrcarpenter.furniture;

import id.cadera.cdrcarpenter.CdrCarpenter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class FurnitureManager {
    private final CdrCarpenter plugin;
    private final FurnitureRegistry registry;

    public FurnitureManager(CdrCarpenter plugin, FurnitureRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public boolean place(Player player, FurnitureDefinition definition, Location base, EquipmentSlot hand) {
        if (definition.barrierCollision() && !base.getBlock().isPassable()) {
            player.sendMessage("§cThere is not enough room to place furniture there.");
            return false;
        }

        float yaw = snapYaw(player.getLocation().getYaw());

        Location anchorLocation = base.clone().add(0.5D, 0.0D, 0.5D);
        anchorLocation.setYaw(yaw);

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

        Interaction interaction = anchorLocation.getWorld().spawn(anchorLocation, Interaction.class, entity -> {
            entity.setPersistent(true);
            entity.setInteractionWidth(definition.hitboxWidth());
            entity.setInteractionHeight(definition.hitboxHeight());
            entity.setResponsive(true);
            tagEntity(entity, definition.id(), instanceId, ownerId);
        });

        display.setPersistent(true);
        interaction.setPersistent(true);

        if (definition.barrierCollision()) {
            base.getBlock().setType(Material.BARRIER, false);
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            consumeOne(player, hand);
        }

        if (plugin.getConfig().getBoolean("debug.log-placements", false)) {
            plugin.getLogger().info(player.getName() + " placed " + definition.id()
                    + " instance=" + instanceId
                    + " barrier=" + definition.barrierCollision()
                    + " visualOffset=(" + definition.visualOffsetX() + ", "
                    + definition.visualOffsetY() + ", "
                    + definition.visualOffsetZ() + ")");
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

        Location barrierLocation = removeInstance(furnitureEntity.getLocation(), instanceId);
        if (definition.barrierCollision() && barrierLocation != null
                && barrierLocation.getBlock().getType() == Material.BARRIER) {
            barrierLocation.getBlock().setType(Material.AIR, false);
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

    public boolean isFurnitureEntity(Entity entity) {
        return entity.getPersistentDataContainer().has(plugin.instanceIdKey(), PersistentDataType.STRING)
                && entity.getPersistentDataContainer().has(plugin.furnitureIdKey(), PersistentDataType.STRING);
    }

    private void tagEntity(Entity entity, String furnitureId, String instanceId, String ownerId) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(plugin.furnitureIdKey(), PersistentDataType.STRING, furnitureId);
        pdc.set(plugin.instanceIdKey(), PersistentDataType.STRING, instanceId);
        pdc.set(plugin.ownerKey(), PersistentDataType.STRING, ownerId);
    }

    private Location removeInstance(Location center, String instanceId) {
        Location interactionBlock = null;
        for (Entity nearby : center.getWorld().getNearbyEntities(center, 3.0D, 3.0D, 3.0D)) {
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
        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        double localX = definition.visualOffsetX();
        double localZ = definition.visualOffsetZ();

        double worldX = (localX * cos) - (localZ * sin);
        double worldZ = (localX * sin) + (localZ * cos);

        return anchor.clone().add(worldX, definition.visualOffsetY(), worldZ);
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
