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
    private static final double EPSILON = 0.0001D;
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

            // If a player somehow starts inside a box (reload, teleport, old placement), let them escape.
            if (overlaps(currentBox, furniture)) {
                continue;
            }

            double deltaY = adjusted.getY() - from.getY();
            boolean horizontalOverlap = overlapsXZ(nextBox, furniture);

            // Landing on top of a custom box: snap feet onto the configured top surface.
            if (deltaY <= 0.0D
                    && horizontalOverlap
                    && currentBox.getMinY() >= furniture.getMaxY() - LANDING_TOLERANCE
                    && nextBox.getMinY() < furniture.getMaxY()) {
                adjusted.setY(furniture.getMaxY() + EPSILON);
                player.setFallDistance(0.0F);
                changed = true;
                continue;
            }

            // Side collision. Resolve X/Z separately so the player can slide along the furniture.
            Location xOnly = adjusted.clone();
            xOnly.setZ(from.getZ());
            Location zOnly = adjusted.clone();
            zOnly.setX(from.getX());

            boolean xSafe = !overlaps(playerBox(xOnly, playerWidthX, playerWidthZ, playerHeight), furniture);
            boolean zSafe = !overlaps(playerBox(zOnly, playerWidthX, playerWidthZ, playerHeight), furniture);

            double dx = adjusted.getX() - from.getX();
            double dz = adjusted.getZ() - from.getZ();

            if (xSafe && zSafe) {
                if (Math.abs(dx) >= Math.abs(dz)) {
                    adjusted.setZ(from.getZ());
                } else {
                    adjusted.setX(from.getX());
                }
            } else if (xSafe) {
                adjusted.setZ(from.getZ());
            } else if (zSafe) {
                adjusted.setX(from.getX());
            } else {
                adjusted.setX(from.getX());
                adjusted.setZ(from.getZ());
            }
            changed = true;
        }

        if (changed) {
            adjusted.setYaw(to.getYaw());
            adjusted.setPitch(to.getPitch());
            event.setTo(adjusted);
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
