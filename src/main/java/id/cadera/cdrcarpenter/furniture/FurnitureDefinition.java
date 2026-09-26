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
        double visualOffsetZ
) {
}
