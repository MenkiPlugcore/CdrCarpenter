package id.cadera.cdrcarpenter.furniture;

import org.bukkit.Material;

public record FurnitureDefinition(
        String id,
        String displayName,
        Material material,
        int customModelData,
        String itemsAdderId,
        float hitboxWidth,
        float hitboxHeight,
        double visualOffsetX,
        double visualOffsetY,
        double visualOffsetZ,
        FurnitureCollisionMode collisionMode,
        double collisionWidth,
        double collisionDepth,
        double collisionHeight,
        double collisionOffsetX,
        double collisionOffsetY,
        double collisionOffsetZ,
        boolean collisionStandable,
        boolean seatEnabled,
        double seatOffsetX,
        double seatOffsetY,
        double seatOffsetZ,
        float seatYawOffset,
        boolean seatCanRotate
) {
}
