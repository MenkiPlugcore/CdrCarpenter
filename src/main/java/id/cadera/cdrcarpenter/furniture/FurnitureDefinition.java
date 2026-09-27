package id.cadera.cdrcarpenter.furniture;

import org.bukkit.Material;

import java.util.Set;

public record FurnitureDefinition(
        String id,
        String displayName,
        Material material,
        int customModelData,
        String itemsAdderId,
        float hitboxWidth,
        float hitboxHeight,
        double hitboxOffsetX,
        double hitboxOffsetY,
        double hitboxOffsetZ,
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
        boolean seatCanRotate,
        boolean storageEnabled,
        int storageRows,
        String storageTitle,
        Set<Material> storageAllowedMaterials,
        boolean workbenchEnabled
) {
}
