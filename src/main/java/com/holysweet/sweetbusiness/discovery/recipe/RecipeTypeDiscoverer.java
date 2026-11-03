package com.holysweet.sweetbusiness.discovery.recipe;

import com.holysweet.sweetbusiness.config.Config;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.HashMap;
import java.util.Map;

public final class RecipeTypeDiscoverer {

    private RecipeTypeDiscoverer() {}

    public enum TierSource {
        OVERRIDE,
        NAMESPACE,
        GLOBAL_DEFAULT
    }

    /**
     * Immutable data record to pass processing properties to the Evaluator.
     * matchedRule identifies the rule that supplied both tier and processing type.
     */
    public record RecipeTypeProperties(int techTier, TierSource source, String matchedRule, boolean isManual) {}

    /**
     * Scans registered RecipeTypes and resolves their tech tiers and manual labor flags
     * based on the cascading TOML configurations.
     */
    public static Map<RecipeType<?>, RecipeTypeProperties> discoverRecipeTypes(MinecraftServer server) {
        Map<RecipeType<?>, RecipeTypeProperties> expectedProperties = new HashMap<>();

        var recipeTypeRegistry = server.registryAccess().registryOrThrow(Registries.RECIPE_TYPE);

        RecipeTypeProperties globalDefaults = getDefaultProperties();
        Map<String, RecipeTypeProperties> namespaceDefaults = parseNamespaceDefaults();
        Map<ResourceLocation, RecipeTypeProperties> overrides = parseOverrides();

        for (RecipeType<?> recipeType : recipeTypeRegistry) {
            ResourceLocation typeId = recipeTypeRegistry.getKey(recipeType);
            if (typeId == null) continue;

            expectedProperties.put(recipeType, overrides.getOrDefault(typeId,
                    namespaceDefaults.getOrDefault(typeId.getNamespace(), globalDefaults)));
        }

        return expectedProperties;
    }

    public static RecipeTypeProperties getDefaultProperties() {
        return new RecipeTypeProperties(Config.GLOBAL_DEFAULT_TIER.get(), TierSource.GLOBAL_DEFAULT,
                "global_default", "manual".equals(Config.GLOBAL_DEFAULT_TYPE.get()));
    }

    private static Map<String, RecipeTypeProperties> parseNamespaceDefaults() {
        Map<String, RecipeTypeProperties> defaults = new HashMap<>();
        for (String entry : Config.NAMESPACE_DEFAULTS.get()) {
            String[] parts = entry.split(":", -1);
            defaults.put(parts[0], parseProperties(parts, 1, TierSource.NAMESPACE, parts[0]));
        }
        return defaults;
    }

    private static Map<ResourceLocation, RecipeTypeProperties> parseOverrides() {
        Map<ResourceLocation, RecipeTypeProperties> overrides = new HashMap<>();
        for (String entry : Config.RECIPE_TYPE_OVERRIDES.get()) {
            String[] parts = entry.split(":", -1);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
            overrides.put(id, parseProperties(parts, 2, TierSource.OVERRIDE, id.toString()));
        }
        return overrides;
    }

    private static RecipeTypeProperties parseProperties(String[] parts, int tierIndex, TierSource source, String matchedRule) {
        return new RecipeTypeProperties(Integer.parseInt(parts[tierIndex]), source, matchedRule,
                "manual".equals(parts[tierIndex + 1]));
    }
}
