package id.cadera.cdrcarpenter.furniture;

import org.bukkit.Material;

public record FurnitureCollisionBlock(
        Material material,
        int offsetX,
        int offsetY,
        int offsetZ
) {
}
