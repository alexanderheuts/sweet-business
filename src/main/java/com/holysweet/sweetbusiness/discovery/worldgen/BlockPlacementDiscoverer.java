package com.holysweet.sweetbusiness.discovery.worldgen;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.BlockPileConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomBooleanFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.ReplaceBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleRandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BlockPlacementDiscoverer {

    private BlockPlacementDiscoverer() {}

    public record PlacementSource(ResourceLocation placedFeatureId, Optional<ResourceLocation> configuredFeatureId,
                                  Set<ResourceLocation> biomes) {
        public PlacementSource {
            biomes = Set.copyOf(biomes);
        }
    }

    public record DiscoveryResult(Map<Block, Set<PlacementSource>> sources,
                                  Map<PlacedFeature, Set<String>> failures) {
        public DiscoveryResult {
            Map<Block, Set<PlacementSource>> sourceSnapshot = new HashMap<>();
            sources.forEach((block, entries) -> sourceSnapshot.put(block, Set.copyOf(entries)));
            sources = Map.copyOf(sourceSnapshot);
            Map<PlacedFeature, Set<String>> failureSnapshot = new HashMap<>();
            failures.forEach((feature, reasons) -> failureSnapshot.put(feature, Set.copyOf(reasons)));
            failures = Map.copyOf(failureSnapshot);
        }
    }

    public static DiscoveryResult discoverBlocks(RegistryAccess registries, WorldGenDiscoverer.DiscoveryResult worldgen) {
        Map<Block, Set<PlacementSource>> sources = new HashMap<>();
        Map<PlacedFeature, Set<String>> failures = new HashMap<>();
        // Traverse each biome feature separately so nested placements retain their original biome source.
        worldgen.featureBiomes().forEach((root, biomes) -> {
            FeatureDiscovery discovery = new FeatureDiscovery(registries, root, Set.copyOf(biomes), sources);
            discovery.discover(root);
            if (!discovery.failures.isEmpty()) failures.put(root, discovery.failures);
        });
        return new DiscoveryResult(sources, failures);
    }

    private static final class FeatureDiscovery {
        private final RegistryAccess registries;
        private final PlacedFeature root;
        private final Set<ResourceLocation> biomes;
        private final Map<Block, Set<PlacementSource>> sources;
        private final Set<PlacedFeature> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<String> failures = new HashSet<>();

        private FeatureDiscovery(RegistryAccess registries, PlacedFeature root, Set<ResourceLocation> biomes,
                                 Map<Block, Set<PlacementSource>> sources) {
            this.registries = registries;
            this.root = root;
            this.biomes = biomes;
            this.sources = sources;
        }

        private void discover(PlacedFeature placed) {
            // Shared or cyclic feature references only need visiting once per root.
            if (!visited.add(placed)) return;
            try {
                discoverConfiguration(placed.feature().value());
            } catch (RuntimeException | LinkageError exception) {
                failures.add(exception.getClass().getName() + ": " + exception.getMessage());
            }
        }

        private void discoverConfiguration(ConfiguredFeature<?, ?> configured) {
            var config = configured.config();
            // Patches and selectors lead to child features; leaf configurations describe the blocks placed.
            switch (config) {
                case TreeConfiguration ignored -> {
                    var tree = TreeFeatureDiscoverer.discoverTree(registries, configured);
                    tree.contents().allStates().forEach(state -> addSource(state.getBlock(), configured));
                    failures.addAll(tree.failures());
                }
                case RandomPatchConfiguration patch -> discover(patch.feature().value());
                case RandomFeatureConfiguration selector -> {
                    // A guaranteed choice makes later choices and the default unreachable.
                    boolean reachable = true;
                    for (var entry : selector.features) {
                        if (reachable && entry.chance > 0) discover(entry.feature.value());
                        if (entry.chance >= 1) reachable = false;
                    }
                    if (reachable) discover(selector.defaultFeature.value());
                }
                case SimpleRandomFeatureConfiguration selector ->
                        selector.features.forEach(feature -> discover(feature.value()));
                case RandomBooleanFeatureConfiguration selector -> {
                    discover(selector.featureTrue.value());
                    discover(selector.featureFalse.value());
                }
                case SimpleBlockConfiguration(var toPlace) -> discoverProvider(toPlace, configured);
                case BlockPileConfiguration pile -> discoverProvider(pile.stateProvider, configured);
                case OreConfiguration ore ->
                        ore.targetStates.forEach(target -> addSource(target.state.getBlock(), configured));
                case ReplaceBlockConfiguration replacement ->
                        replacement.targetStates.forEach(target -> addSource(target.state.getBlock(), configured));
                default -> failures.add("Unsupported feature configuration: " + config.getClass().getName());
            }
        }

        private void discoverProvider(BlockStateProvider provider, ConfiguredFeature<?, ?> configured) {
            // Read declared states through the codec so rare provider outputs are not missed by sampling.
            BlockStateProviderDiscoverer.discoverStates(registries, provider)
                    .forEach(state -> addSource(state.getBlock(), configured));
        }

        private void addSource(Block block, ConfiguredFeature<?, ?> configured) {
            // Inline child features may lack an ID; the registered root still identifies their source.
            ResourceLocation placedId = registries.registryOrThrow(Registries.PLACED_FEATURE).getKey(root);
            if (placedId == null) throw new IllegalStateException("Unregistered root placed feature");
            ResourceLocation configuredId = registries.registryOrThrow(Registries.CONFIGURED_FEATURE).getKey(configured);
            PlacementSource source = new PlacementSource(placedId, Optional.ofNullable(configuredId), biomes);
            sources.computeIfAbsent(block, key -> new HashSet<>()).add(source);
        }
    }

}
