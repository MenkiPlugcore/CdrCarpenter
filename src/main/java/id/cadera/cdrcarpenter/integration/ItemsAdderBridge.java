package id.cadera.cdrcarpenter.integration;

import id.cadera.cdrcarpenter.CdrCarpenter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public final class ItemsAdderBridge {
    private final CdrCarpenter plugin;
    private final boolean available;
    private Method getInstanceMethod;
    private Method getItemStackMethod;
    private boolean warned;

    public ItemsAdderBridge(CdrCarpenter plugin) {
        this.plugin = plugin;
        Plugin itemsAdder = plugin.getServer().getPluginManager().getPlugin("ItemsAdder");
        this.available = itemsAdder != null && itemsAdder.isEnabled() && prepare(itemsAdder);
    }

    private boolean prepare(Plugin itemsAdder) {
        try {
            Class<?> customStackClass = Class.forName(
                    "dev.lone.itemsadder.api.CustomStack",
                    true,
                    itemsAdder.getClass().getClassLoader()
            );
            getInstanceMethod = customStackClass.getMethod("getInstance", String.class);
            getItemStackMethod = customStackClass.getMethod("getItemStack");
            return true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("ItemsAdder detected, but its CustomStack API could not be initialized: " + exception.getMessage());
            return false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public ItemStack createItem(String namespacedId, int amount) {
        if (!available || namespacedId == null || namespacedId.isBlank()) {
            return null;
        }

        try {
            Object customStack = getInstanceMethod.invoke(null, namespacedId);
            if (customStack == null) {
                return null;
            }
            ItemStack item = ((ItemStack) getItemStackMethod.invoke(customStack)).clone();
            item.setAmount(Math.max(1, amount));
            return item;
        } catch (ReflectiveOperationException | ClassCastException exception) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("ItemsAdder item creation failed; vanilla fallback will be used: " + exception.getMessage());
            }
            return null;
        }
    }
}
