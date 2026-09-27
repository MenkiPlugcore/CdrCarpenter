package id.cadera.cdrcarpenter.furniture;

import java.util.Locale;

public enum FurnitureCollisionMode {
    NONE,
    BARRIER,
    CUSTOM,
    BLOCK;

    public static FurnitureCollisionMode parse(String raw, FurnitureCollisionMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
