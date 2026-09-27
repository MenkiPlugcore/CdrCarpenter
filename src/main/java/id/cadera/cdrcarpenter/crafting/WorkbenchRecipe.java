package id.cadera.cdrcarpenter.crafting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record WorkbenchRecipe(
        String id,
        String outputFurnitureId,
        Map<String, Integer> ingredients
) {
    public WorkbenchRecipe {
        ingredients = Collections.unmodifiableMap(new LinkedHashMap<>(ingredients));
    }
}
