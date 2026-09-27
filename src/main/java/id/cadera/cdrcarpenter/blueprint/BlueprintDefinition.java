package id.cadera.cdrcarpenter.blueprint;

public record BlueprintDefinition(
        String id,
        String displayName,
        String recipeId,
        String itemsAdderId
) {
}
