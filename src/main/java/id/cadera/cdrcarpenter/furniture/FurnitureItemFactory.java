package id.cadera.cdrcarpenter.furniture;

import id.cadera.cdrcarpenter.CdrCarpenter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class FurnitureItemFactory {
    private FurnitureItemFactory() {
    }

    public static ItemStack create(CdrCarpenter plugin, FurnitureDefinition definition, int amount) {
        ItemStack item = new ItemStack(definition.material(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(definition.displayName(), NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("CdrCarpenter Furniture", NamedTextColor.DARK_GRAY),
                Component.text("Right-click a block to place", NamedTextColor.GRAY),
                Component.text("Sneak + right-click to pick up", NamedTextColor.GRAY)
        ));

        if (definition.customModelData() > 0) {
            var customModelData = meta.getCustomModelDataComponent();
            customModelData.setFloats(List.of((float) definition.customModelData()));
            meta.setCustomModelDataComponent(customModelData);
        }

        meta.getPersistentDataContainer().set(plugin.furnitureItemKey(), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(plugin.furnitureIdKey(), PersistentDataType.STRING, definition.id());
        item.setItemMeta(meta);
        return item;
    }

    public static String getFurnitureId(CdrCarpenter plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        Byte marker = meta.getPersistentDataContainer().get(plugin.furnitureItemKey(), PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) {
            return null;
        }
        return meta.getPersistentDataContainer().get(plugin.furnitureIdKey(), PersistentDataType.STRING);
    }
}
