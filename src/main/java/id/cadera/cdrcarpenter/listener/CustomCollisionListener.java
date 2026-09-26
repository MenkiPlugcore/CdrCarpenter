package id.cadera.cdrcarpenter.listener;

import id.cadera.cdrcarpenter.furniture.FurnitureCollisionMode;
import id.cadera.cdrcarpenter.furniture.FurnitureDefinition;
import id.cadera.cdrcarpenter.furniture.FurnitureManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.BoundingBox;

public final class CustomCollisionListener implements Listener {
    private static final double EPSILON = 0.002D;
    private static final double LANDING_TOLERANCE = 0.12D;

    private final FurnitureManager manager;

    public CustomCollisionListener(FurnitureManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() != to.getWorld()) {
            return;
        }

        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR || manager.isCollisionBypassed(player)) {
            return;
        }

        Location adjusted = to.clone();
        boolean changed = false;

        BoundingBox live = player.getBoundingBox();
        double playerWidthX = Math.max(0.20D, live.getWidthX());
        double playerWidthZ = Math.max(0.20D, live.getWidthZ());
        double playerHeight = Math.max(0.20D, live.getHeight());
        double halfX = playerWidthX / 2.0D;
        double halfZ = playerWidthZ / 2.0D;

        for (Entity entity : to.getWorld().getNearbyEntities(to, 4.5D, 3.5D, 4.5D)) {
            if (!(entity instanceof Interaction interaction) || !manager.isFurnitureEntity(interaction)) {
                continue;
            }

            FurnitureDefinition definition = manager.definitionFor(interaction);
            if (definition == null || definition.collisionMode() != FurnitureCollisionMode.CUSTOM) {
                continue;
            }

            BoundingBox furniture = manager.customCollisionBox(interaction, definition);
            BoundingBox currentBox = playerBox(from, playerWidthX, playerWidthZ, playerHeight);
            BoundingBox nextBox = playerBox(adjusted, playerWidthX, playerWidthZ, playerHeight);

            if (!overlaps(nextBox, furniture)) {
                continue;
            }

            double deltaY = adjusted.getY() - from.getY();
            boolean horizontalOverlap = overlapsXZ(nextBox, furniture);

            // Optional top surface. Chairs default to standable=false so movement never rubber-bands on the seat.
            if (definition.collisionStandable()
                    && deltaY <= 0.0D
                    && horizontalOverlap
                    && currentBox.getMinY() >= furniture.getMaxY() - LANDING_TOLERANCE
                    && nextBox.getMinY() < furniture.getMaxY()) {
                adjusted.setY(furniture.getMaxY() + EPSILON);
                player.setFallDistance(0.0F);
                changed = true;
                continue;
            }

            // If a player is already intersecting the box, eject them to the nearest horizontal side.
            // This handles reloads, teleports, config changes, and prevents permanent soft-locks.
            if (overlaps(currentBox, furniture)) {
                pushOutNearest(adjusted, furniture, halfX, halfZ);
                changed = true;
                continue;
            }

            // Normal side collision: resolve to the nearest legal edge instead of cancelling movement.
            pushOutNearest(adjusted, furniture, halfX, halfZ);
            changed = true;
        }

        if (changed) {
            adjusted.setYaw(to.getYaw());
            adjusted.setPitch(to.getPitch());
            event.setTo(adjusted);
        }
    }

    private void pushOutNearest(Location location, BoundingBox furniture, double halfX, double halfZ) {
        double left = furniture.getMinX() - halfX - EPSILON;
        double right = furniture.getMaxX() + halfX + EPSILON;
        double back = furniture.getMinZ() - halfZ - EPSILON;
        double front = furniture.getMaxZ() + halfZ + EPSILON;

        double dLeft = Math.abs(location.getX() - left);
        double dRight = Math.abs(location.getX() - right);
        double dBack = Math.abs(location.getZ() - back);
        double dFront = Math.abs(location.getZ() - front);

        double min = Math.min(Math.min(dLeft, dRight), Math.min(dBack, dFront));
        if (min == dLeft) {
            location.setX(left);
        } else if (min == dRight) {
            location.setX(right);
        } else if (min == dBack) {
            location.setZ(back);
        } else {
            location.setZ(front);
        }
    }

    private BoundingBox playerBox(Location location, double widthX, double widthZ, double height) {
        double halfX = widthX / 2.0D;
        double halfZ = widthZ / 2.0D;
        return new BoundingBox(
                location.getX() - halfX,
                location.getY(),
                location.getZ() - halfZ,
                location.getX() + halfX,
                location.getY() + height,
                location.getZ() + halfZ
        );
    }

    private boolean overlaps(BoundingBox a, BoundingBox b) {
        return a.getMaxX() > b.getMinX() + EPSILON
                && a.getMinX() < b.getMaxX() - EPSILON
                && a.getMaxY() > b.getMinY() + EPSILON
                && a.getMinY() < b.getMaxY() - EPSILON
                && a.getMaxZ() > b.getMinZ() + EPSILON
                && a.getMinZ() < b.getMaxZ() - EPSILON;
    }

    private boolean overlapsXZ(BoundingBox a, BoundingBox b) {
        return a.getMaxX() > b.getMinX() + EPSILON
                && a.getMinX() < b.getMaxX() - EPSILON
                && a.getMaxZ() > b.getMinZ() + EPSILON
                && a.getMinZ() < b.getMaxZ() - EPSILON;
    }
}
