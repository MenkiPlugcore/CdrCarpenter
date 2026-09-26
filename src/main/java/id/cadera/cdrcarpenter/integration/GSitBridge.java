package id.cadera.cdrcarpenter.integration;

import id.cadera.cdrcarpenter.CdrCarpenter;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public final class GSitBridge {
    private enum ApiMode {
        NONE,
        LEGACY_35,
        CUSTOM_36_PLUS
    }

    private final CdrCarpenter plugin;
    private final boolean available;
    private final String pluginVersion;
    private ApiMode apiMode = ApiMode.NONE;
    private Method createSeatMethod;
    private Method isEntitySittingMethod;
    private boolean warned;

    public GSitBridge(CdrCarpenter plugin) {
        this.plugin = plugin;
        Plugin gsit = plugin.getServer().getPluginManager().getPlugin("GSit");
        this.pluginVersion = gsit == null ? "unknown" : gsit.getPluginMeta().getVersion();
        this.available = gsit != null && gsit.isEnabled() && prepare(gsit);
    }

    private boolean prepare(Plugin gsit) {
        try {
            ClassLoader loader = gsit.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("dev.geco.gsit.api.GSitAPI", true, loader);
            isEntitySittingMethod = apiClass.getMethod("isEntitySitting", LivingEntity.class);

            // GSit 3.6.0+ exposes createCustomSeat(..., force, canRotate, offsets..., rotation, centered).
            try {
                createSeatMethod = apiClass.getMethod(
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
                apiMode = ApiMode.CUSTOM_36_PLUS;
                return true;
            } catch (NoSuchMethodException ignored) {
                // Fall through to the API used by GSit 3.5.x.
            }

            // GSit 3.5.x and older supported releases expose createSeat(..., canRotate, offsets..., rotation, centered).
            createSeatMethod = apiClass.getMethod(
                    "createSeat",
                    Block.class,
                    LivingEntity.class,
                    boolean.class,
                    double.class,
                    double.class,
                    double.class,
                    float.class,
                    boolean.class
            );
            apiMode = ApiMode.LEGACY_35;
            return true;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("GSit " + pluginVersion
                    + " detected, but no compatible seat API could be initialized: " + exception.getMessage());
            apiMode = ApiMode.NONE;
            return false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String statusDescription() {
        if (!available) {
            return "not detected/unsupported - seating disabled";
        }
        return switch (apiMode) {
            case LEGACY_35 -> "detected v" + pluginVersion + " - legacy 3.5.x seating enabled";
            case CUSTOM_36_PLUS -> "detected v" + pluginVersion + " - custom seating enabled";
            default -> "detected v" + pluginVersion + " - seating unavailable";
        };
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
        if (!available || createSeatMethod == null) {
            return false;
        }

        // CdrCarpenter's Interaction anchor lives in the air block occupied by the furniture.
        // GSit's centered seat math is based on a physical block, so use the supporting block
        // when the supplied anchor block is passable. This keeps 3.5.x and 3.6+ aligned.
        Block seatBlock = resolveSeatBlock(block);

        try {
            Object seat;
            if (apiMode == ApiMode.CUSTOM_36_PLUS) {
                seat = createSeatMethod.invoke(
                        null,
                        seatBlock,
                        entity,
                        true,       // force custom seat creation
                        canRotate,
                        xOffset,
                        yOffset,
                        zOffset,
                        rotation,
                        true        // center on the supporting block
                );
            } else {
                seat = createSeatMethod.invoke(
                        null,
                        seatBlock,
                        entity,
                        canRotate,
                        xOffset,
                        yOffset,
                        zOffset,
                        rotation,
                        true        // center on the supporting block
                );
            }
            return seat != null;
        } catch (ReflectiveOperationException | IllegalArgumentException exception) {
            warnOnce("GSit " + pluginVersion + " seat creation failed: " + rootMessage(exception));
            return false;
        }
    }

    public boolean isSitting(LivingEntity entity) {
        if (!available || isEntitySittingMethod == null) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(isEntitySittingMethod.invoke(null, entity));
        } catch (ReflectiveOperationException | IllegalArgumentException exception) {
            warnOnce("GSit " + pluginVersion + " sitting-state check failed: " + rootMessage(exception));
            return false;
        }
    }

    private Block resolveSeatBlock(Block anchorBlock) {
        if (!anchorBlock.isPassable()) {
            return anchorBlock;
        }

        Block below = anchorBlock.getRelative(BlockFace.DOWN);
        if (!below.isPassable()) {
            return below;
        }

        // Fallback for unusual floating furniture. The API call may still reject the location,
        // but we avoid silently attaching the seat to an unrelated block.
        return anchorBlock;
    }

    private String rootMessage(Throwable throwable) {
        Throwable cause = throwable.getCause();
        if (cause != null && cause.getMessage() != null) {
            return cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private void warnOnce(String message) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().warning(message);
    }
}
