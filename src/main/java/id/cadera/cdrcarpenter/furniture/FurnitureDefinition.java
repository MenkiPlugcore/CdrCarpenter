package id.cadera.cdrcarpenter.furniture;

import org.bukkit.Material;

public record FurnitureDefinition(
        String id,
        String displayName,
        Material material,
        int customModelData,
        float hitboxWidth,
        float hitboxHeight
) {
}
