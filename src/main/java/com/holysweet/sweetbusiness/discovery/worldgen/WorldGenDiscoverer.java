package com.holysweet.sweetbusiness.discovery.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WorldGenDiscoverer {

    private WorldGenDiscoverer() {}

    public record DiscoveryResult(int totalBiomes, Map<PlacedFeature, List<ResourceLocation>> featureBiomes) {
        public DiscoveryResult {
            Map<PlacedFeature, List<ResourceLocation>> snapshot = new HashMap<>();
            featureBiomes.forEach((feature, biomes) -> snapshot.put(feature, List.copyOf(biomes)));
            featureBiomes = Map.copyOf(snapshot);
        }
    }

    public static DiscoveryResult discoverFeatures(MinecraftServer server) {
        var biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
        var featureRegistry = server.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        Map<PlacedFeature, List<ResourceLocation>> featureBiomes = new HashMap<>();

        for (var biome : biomeRegistry) {
            ResourceLocation biomeId = biomeRegistry.getKey(biome);
            for (var step : biome.getGenerationSettings().features()) {
                for (var holder : step) {
                    featureBiomes.computeIfAbsent(holder.value(), key -> new ArrayList<>()).add(biomeId);
                }
            }
        }
        // Preserve the existing scope: only registered placed features participate.
        featureBiomes.keySet().removeIf(feature -> featureRegistry.getKey(feature) == null);
        return new DiscoveryResult(biomeRegistry.size(), featureBiomes);
    }
}
