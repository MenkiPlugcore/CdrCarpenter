package id.cadera.cdrcarpenter.seating;

import id.cadera.cdrcarpenter.CdrCarpenter;
import id.cadera.cdrcarpenter.furniture.FurnitureDefinition;
import id.cadera.cdrcarpenter.furniture.FurnitureManager;
import id.cadera.cdrcarpenter.integration.GSitBridge;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class SeatingManager implements Listener {
    private final CdrCarpenter plugin;
    private final FurnitureManager furnitureManager;
    private final GSitBridge gsit;
    private final Map<UUID, String> playerSeats = new HashMap<>();
    private final Map<String, UUID> occupiedFurniture = new HashMap<>();
    private BukkitTask monitorTask;

    public SeatingManager(CdrCarpenter plugin, FurnitureManager furnitureManager, GSitBridge gsit) {
        this.plugin = plugin;
        this.furnitureManager = furnitureManager;
        this.gsit = gsit;
    }

    public void start() {
        if (!gsit.isAvailable()) {
            return;
        }
        monitorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanupEndedSeats, 5L, 5L);
    }

    public void shutdown() {
        if (monitorTask != null) {
            monitorTask.cancel();
            monitorTask = null;
        }
        for (UUID playerId : playerSeats.keySet()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                furnitureManager.setCollisionBypass(player, false);
            }
        }
        playerSeats.clear();
        occupiedFurniture.clear();
    }

    public boolean sit(Player player, Interaction interaction) {
        FurnitureDefinition definition = furnitureManager.definitionFor(interaction);
        if (definition == null || !definition.seatEnabled()) {
            return false;
        }

        if (!gsit.isAvailable()) {
            player.sendMessage("§cThis seat requires GSit, but GSit is not available.");
            return true;
        }

        if (gsit.isSitting(player)) {
            player.sendMessage("§eYou are already sitting.");
            return true;
        }

        String instanceId = interaction.getPersistentDataContainer().get(plugin.instanceIdKey(), PersistentDataType.STRING);
        if (instanceId == null) {
            return false;
        }

        UUID occupantId = occupiedFurniture.get(instanceId);
        if (occupantId != null) {
            Player occupant = plugin.getServer().getPlayer(occupantId);
            if (occupant != null && occupant.isOnline() && gsit.isSitting(occupant)) {
                player.sendMessage("§eSomeone is already sitting here.");
                return true;
            }
            release(occupantId);
        }

        float chairYaw = interaction.getLocation().getYaw();
        double radians = Math.toRadians(chairYaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double worldX = (definition.seatOffsetX() * cos) - (definition.seatOffsetZ() * sin);
        double worldZ = (definition.seatOffsetX() * sin) + (definition.seatOffsetZ() * cos);
        float seatYaw = normalizeYaw(chairYaw + definition.seatYawOffset());

        furnitureManager.setCollisionBypass(player, true);
        boolean created = gsit.createCustomSeat(
                interaction.getLocation().getBlock(),
                player,
                definition.seatCanRotate(),
                worldX,
                definition.seatOffsetY(),
                worldZ,
                seatYaw
        );

        if (!created) {
            furnitureManager.setCollisionBypass(player, false);
            player.sendMessage("§cCould not create a seat here.");
            return true;
        }

        playerSeats.put(player.getUniqueId(), instanceId);
        occupiedFurniture.put(instanceId, player.getUniqueId());
        return true;
    }

    public boolean isOccupied(Interaction interaction) {
        String instanceId = interaction.getPersistentDataContainer().get(plugin.instanceIdKey(), PersistentDataType.STRING);
        if (instanceId == null) {
            return false;
        }
        UUID playerId = occupiedFurniture.get(instanceId);
        if (playerId == null) {
            return false;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline() && gsit.isSitting(player)) {
            return true;
        }
        release(playerId);
        return false;
    }

    private void cleanupEndedSeats() {
        Iterator<Map.Entry<UUID, String>> iterator = playerSeats.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, String> entry = iterator.next();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null && player.isOnline() && gsit.isSitting(player)) {
                continue;
            }

            if (player != null) {
                furnitureManager.setCollisionBypass(player, false);
            }
            occupiedFurniture.remove(entry.getValue(), entry.getKey());
            iterator.remove();
        }
    }

    private void release(UUID playerId) {
        if (playerId == null) {
            return;
        }
        String instanceId = playerSeats.remove(playerId);
        if (instanceId != null) {
            occupiedFurniture.remove(instanceId, playerId);
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            furnitureManager.setCollisionBypass(player, false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        release(event.getPlayer().getUniqueId());
    }

    private float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        if (normalized > 180.0F) {
            normalized -= 360.0F;
        } else if (normalized < -180.0F) {
            normalized += 360.0F;
        }
        return normalized;
    }
}
