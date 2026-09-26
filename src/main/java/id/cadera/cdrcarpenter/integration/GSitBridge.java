package id.cadera.cdrcarpenter.integration;

import id.cadera.cdrcarpenter.CdrCarpenter;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public final class GSitBridge {
    private final CdrCarpenter plugin;
    private final boolean available;
    private Method createCustomSeatMethod;
    private Method isEntitySittingMethod;
    private boolean warned;

    public GSitBridge(CdrCarpenter plugin) {
        this.plugin = plugin;
        Plugin gsit = plugin.getServer().getPluginManager().getPlugin("GSit");
        this.available = gsit != null && gsit.isEnabled() && prepare(gsit);
    }

    private boolean prepare(Plugin gsit) {
        try {
            ClassLoader loader = gsit.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("dev.geco.gsit.api.GSitAPI", true, loader);
            createCustomSeatMethod = apiClass.getMethod(
                    "createCustomSeat",
                    Block.class,
                    LivingEntity.class,
                    boolean.class,
                    boolean.class,
                    double.class,
                    double.class,
                    double.class,
                    float.class,
                    boolean.class
            );
            isEntitySittingMethod = apiClass.getMethod("isEntitySitting", LivingEntity.class);
            return true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("GSit detected, but its custom-seat API could not be initialized: " + exception.getMessage());
            return false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean createCustomSeat(
            Block block,
            LivingEntity entity,
            boolean canRotate,
            double xOffset,
            double yOffset,
            double zOffset,
            float rotation
    ) {
        if (!available) {
            return false;
        }

        try {
            Object seat = createCustomSeatMethod.invoke(
                    null,
                    block,
                    entity,
                    true,
                    canRotate,
                    xOffset,
                    yOffset,
                    zOffset,
                    rotation,
                    true
            );
            return seat != null;
        } catch (ReflectiveOperationException exception) {
            warnOnce("GSit seat creation failed: " + exception.getMessage());
            return false;
        }
    }

    public boolean isSitting(LivingEntity entity) {
        if (!available) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(isEntitySittingMethod.invoke(null, entity));
        } catch (ReflectiveOperationException exception) {
            warnOnce("GSit sitting-state check failed: " + exception.getMessage());
            return false;
        }
    }

    private void warnOnce(String message) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().warning(message);
    }
}
