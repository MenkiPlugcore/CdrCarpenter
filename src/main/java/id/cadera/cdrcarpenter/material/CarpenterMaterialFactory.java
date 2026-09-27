package id.cadera.cdrcarpenter.material;

import id.cadera.cdrcarpenter.CdrCarpenter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class CarpenterMaterialFactory {
    public static final String PROCESSED_WOOD = "processed_wood";

    private CarpenterMaterialFactory() {
    }

    public static ItemStack create(CdrCarpenter plugin, String id, int amount) {
        if (!PROCESSED_WOOD.equals(id)) {
            return null;
        }

        ItemStack item = plugin.itemsAdderBridge().createItem("cdrcarpenter:processed_wood", amount);
        if (item == null) {
            item = new ItemStack(Material.OAK_SLAB, Math.max(1, amount));
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Processed Wood Board");
            meta.setLore(List.of(
                    "§7Cut and prepared at a Carpenter Sawmill.",
                    "§8CdrCarpenter material"
            ));
            meta.getPersistentDataContainer().set(plugin.materialItemKey(), PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        item.setAmount(Math.max(1, amount));
        return item;
    }

    public static String getId(CdrCarpenter plugin, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(plugin.materialItemKey(), PersistentDataType.STRING);
    }

    public static boolean is(CdrCarpenter plugin, ItemStack item, String id) {
        return id != null && id.equals(getId(plugin, item));
    }

    public static String displayName(String id) {
        if (PROCESSED_WOOD.equals(id)) {
            return "Processed Wood Board";
        }
        return id;
    }
}
