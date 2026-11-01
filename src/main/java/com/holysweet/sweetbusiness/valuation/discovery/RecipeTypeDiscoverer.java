package com.holysweet.sweetbusiness.valuation.discovery;

import com.holysweet.sweetbusiness.config.Config;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class RecipeTypeDiscoverer {

    private RecipeTypeDiscoverer() {}

    public enum TierSource {
        OVERRIDE,
        NAMESPACE,
        GLOBAL_DEFAULT
    }

    /**
     * Immutable data record to pass processing properties to the Evaluator.
     * matchedRule contains the exact string that triggered the tier (e.g., "minecraft:smithing" or "create").
     */
    public record RecipeTypeProperties(int techTier, TierSource source, String matchedRule, boolean isManual) {}

    private record TierResult(int tier, TierSource source, String matchedRule) {}

    /**
     * Scans registered RecipeTypes and resolves their tech tiers and manual labor flags
     * based on the cascading TOML configurations.
     */
    public static Map<RecipeType<?>, RecipeTypeProperties> discoverRecipeTypes(MinecraftServer server) {
        Map<RecipeType<?>, RecipeTypeProperties> expectedProperties = new HashMap<>();

        var recipeTypeRegistry = server.registryAccess().registryOrThrow(Registries.RECIPE_TYPE);

        // 1. Pre-parse configuration values into fast-lookup maps
        int globalDefaultTier = Config.GLOBAL_DEFAULT_TIER.get();
        Map<String, Integer> namespaceTiers = parseNamespaceTiers();
        Map<ResourceLocation, Integer> overrideTiers = parseOverrideTiers();
        Set<ResourceLocation> manualTypes = parseManualTypes();

        // 2. Evaluate every loaded RecipeType in the registry
        for (RecipeType<?> recipeType : recipeTypeRegistry) {
            ResourceLocation typeId = recipeTypeRegistry.getKey(recipeType);
            if (typeId == null) continue;

            TierResult tierResult = resolveTier(typeId, globalDefaultTier, namespaceTiers, overrideTiers);
            boolean isManual = manualTypes.contains(typeId);

            expectedProperties.put(recipeType, new RecipeTypeProperties(
                    tierResult.tier(),
                    tierResult.source(),
                    tierResult.matchedRule(),
                    isManual
            ));
        }

        return expectedProperties;
    }

    private static Map<String, Integer> parseNamespaceTiers() {
        Map<String, Integer> tiers = new HashMap<>();
        for (String entry : Config.NAMESPACE_DEFAULTS.get()) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                try {
                    tiers.put(parts[0], Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {}
            }
        }
        return tiers;
    }

    private static Map<ResourceLocation, Integer> parseOverrideTiers() {
        Map<ResourceLocation, Integer> overrides = new HashMap<>();
        for (String entry : Config.RECIPE_TYPE_OVERRIDES.get()) {
            String[] parts = entry.split(":");
            if (parts.length == 3) {
                try {
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
                    overrides.put(id, Integer.parseInt(parts[2]));
                } catch (NumberFormatException ignored) {}
            }
        }
        return overrides;
    }

    private static Set<ResourceLocation> parseManualTypes() {
        Set<ResourceLocation> manualTypes = new HashSet<>();
        for (String entry : Config.MANUAL_RECIPE_TYPES.get()) {
            try {
                manualTypes.add(ResourceLocation.parse(entry));
            } catch (Exception ignored) {}
        }
        return manualTypes;
    }

    private static TierResult resolveTier(ResourceLocation id, int globalDefault, Map<String, Integer> namespaceTiers, Map<ResourceLocation, Integer> overrideTiers) {
        if (overrideTiers.containsKey(id)) {
            return new TierResult(overrideTiers.get(id), TierSource.OVERRIDE, id.toString());
        }

        String namespace = id.getNamespace();
        if (namespaceTiers.containsKey(namespace)) {
            return new TierResult(namespaceTiers.get(namespace), TierSource.NAMESPACE, namespace);
        }

        return new TierResult(globalDefault, TierSource.GLOBAL_DEFAULT, "global_default");
    }
}