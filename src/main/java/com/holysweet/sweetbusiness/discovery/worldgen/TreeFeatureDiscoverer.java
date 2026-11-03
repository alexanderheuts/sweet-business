package com.holysweet.sweetbusiness.discovery.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.rootplacers.RootPlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;

public final class TreeFeatureDiscoverer {

    private TreeFeatureDiscoverer() {}

    public enum Part { TRUNK, FOLIAGE, ROOT, DECORATION, GROUND }

    public record TreeContents(Map<Part, Set<BlockState>> parts) {
        public TreeContents {
            Map<Part, Set<BlockState>> snapshot = new EnumMap<>(Part.class);
            parts.forEach((part, states) -> snapshot.put(part, Set.copyOf(states)));
            parts = Map.copyOf(snapshot);
        }

        public Set<BlockState> allStates() {
            Set<BlockState> states = new HashSet<>();
            parts.values().forEach(states::addAll);
            return Set.copyOf(states);
        }
    }

    public record DiscoveryResult(Map<ResourceLocation, TreeContents> trees,
                                  Map<ResourceLocation, Set<String>> failures) {
        public DiscoveryResult {
            trees = Map.copyOf(trees);
            Map<ResourceLocation, Set<String>> snapshot = new HashMap<>();
            failures.forEach((id, reasons) -> snapshot.put(id, Set.copyOf(reasons)));
            failures = Map.copyOf(snapshot);
        }
    }

    public static DiscoveryResult discoverFeatures(RegistryAccess registries, Set<ResourceLocation> featureIds) {
        Map<ResourceLocation, TreeContents> trees = new HashMap<>();
        Map<ResourceLocation, Set<String>> failures = new HashMap<>();
        var registry = registries.registryOrThrow(Registries.CONFIGURED_FEATURE);
        for (ResourceLocation id : featureIds) {
            Set<String> reasons = new HashSet<>();
            try {
                var feature = registry.getOptional(id).orElseThrow(() -> new IllegalStateException("Missing tree feature: " + id));
                var discovery = discoverTree(registries, feature);
                trees.put(id, discovery.contents());
                reasons.addAll(discovery.failures());
            } catch (RuntimeException | LinkageError exception) {
                reasons.add(describe(exception));
            }
            if (!reasons.isEmpty()) failures.put(id, reasons);
        }
        return new DiscoveryResult(trees, failures);
    }

    public record TreeDiscovery(TreeContents contents, Set<String> failures) {
        public TreeDiscovery {
            failures = Set.copyOf(failures);
        }
    }

    public static TreeDiscovery discoverTree(RegistryAccess registries, ConfiguredFeature<?, ?> feature) {
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        Set<String> reasons = new HashSet<>();
        Map<Part, Set<BlockState>> parts = new EnumMap<>(Part.class);
        try {
            if (feature.config() instanceof TreeConfiguration tree) {
                if (feature.feature().getClass() != TreeFeature.class) {
                    reasons.add("Custom tree feature may have additional outputs: " + feature.feature().getClass().getName());
                }
                readProvider(ops, tree.trunkProvider, Part.TRUNK, parts, reasons);
                readProvider(ops, tree.foliageProvider, Part.FOLIAGE, parts, reasons);
                readProvider(ops, tree.dirtProvider, Part.GROUND, parts, reasons);
                tree.rootPlacer.ifPresent(root -> readRoots(ops, root, parts, reasons));
                tree.decorators.forEach(decorator -> readDecorator(ops, decorator, parts, reasons));
                reportCustomPlacers(ops, tree, reasons);
            } else {
                reasons.add("Unsupported tree feature configuration: " + feature.config().getClass().getName());
            }
        } catch (RuntimeException | LinkageError exception) {
            reasons.add(describe(exception));
        }
        return new TreeDiscovery(new TreeContents(parts), reasons);
    }

    private static void readProvider(DynamicOps<JsonElement> ops, BlockStateProvider provider, Part part,
                                     Map<Part, Set<BlockState>> parts, Set<String> failures) {
        try {
            readProvider(ops, BlockStateProvider.CODEC.encodeStart(ops, provider).getOrThrow(), part, parts);
        } catch (RuntimeException | LinkageError exception) {
            failures.add(part + ": " + describe(exception));
        }
    }

    private static void reportCustomPlacers(DynamicOps<JsonElement> ops, TreeConfiguration tree, Set<String> failures) {
        try {
            JsonObject definition = TreeConfiguration.CODEC.encodeStart(ops, tree).getOrThrow().getAsJsonObject();
            for (String component : List.of("trunk_placer", "foliage_placer")) {
                String type = definition.getAsJsonObject(component).get("type").getAsString();
                if (!type.startsWith("minecraft:")) failures.add("Custom " + component + " may have additional outputs: " + type);
            }
        } catch (RuntimeException | LinkageError exception) {
            failures.add("Tree placers: " + describe(exception));
        }
    }

    private static void readProvider(DynamicOps<JsonElement> ops, JsonElement provider, Part part,
                                     Map<Part, Set<BlockState>> parts) {
        Set<BlockState> states = parts.computeIfAbsent(part, key -> new HashSet<>());
        states.addAll(BlockStateProviderDiscoverer.discoverStates(ops, provider));
        if (part == Part.DECORATION) {
            // Hanging mangrove propagules can mature after placement, making the planting item harvestable.
            Set<BlockState> mature = new HashSet<>();
            for (BlockState state : states) {
                if (state.getBlock() instanceof MangrovePropaguleBlock) {
                    mature.add(state.setValue(MangrovePropaguleBlock.AGE, 4));
                }
            }
            states.addAll(mature);
        }
    }

    private static void readRoots(DynamicOps<JsonElement> ops, RootPlacer root,
                                  Map<Part, Set<BlockState>> parts, Set<String> failures) {
        try {
            JsonObject definition = RootPlacer.CODEC.encodeStart(ops, root).getOrThrow().getAsJsonObject();
            readProvider(ops, definition.get("root_provider"), Part.ROOT, parts);
            if (definition.has("above_root_placement")) {
                JsonObject above = definition.getAsJsonObject("above_root_placement");
                if (above.get("above_root_placement_chance").getAsDouble() > 0) {
                    readProvider(ops, above.get("above_root_provider"), Part.ROOT, parts);
                }
            }
            if (definition.has("mangrove_root_placement")) {
                readProvider(ops, definition.getAsJsonObject("mangrove_root_placement").get("muddy_roots_provider"),
                        Part.ROOT, parts);
            }
            if (!definition.get("type").getAsString().equals("minecraft:mangrove_root_placer")) {
                failures.add("Custom root placer may have additional outputs: " + root.getClass().getName());
            }
        } catch (RuntimeException | LinkageError exception) {
            failures.add("Roots: " + describe(exception));
        }
    }

    private static void readDecorator(DynamicOps<JsonElement> ops, TreeDecorator decorator,
                                      Map<Part, Set<BlockState>> parts, Set<String> failures) {
        try {
            JsonObject definition = TreeDecorator.CODEC.encodeStart(ops, decorator).getOrThrow().getAsJsonObject();
            if (definition.has("probability") && definition.get("probability").getAsDouble() <= 0) return;
            switch (definition.get("type").getAsString()) {
                case "minecraft:attached_to_leaves" -> readProvider(ops, definition.get("block_provider"), Part.DECORATION, parts);
                case "minecraft:alter_ground" -> readProvider(ops, definition.get("provider"), Part.GROUND, parts);
                case "minecraft:trunk_vine", "minecraft:leave_vine" ->
                        parts.computeIfAbsent(Part.DECORATION, key -> new HashSet<>()).add(Blocks.VINE.defaultBlockState());
                case "minecraft:beehive" ->
                        parts.computeIfAbsent(Part.DECORATION, key -> new HashSet<>()).add(Blocks.BEE_NEST.defaultBlockState());
                case "minecraft:cocoa" -> {
                    for (int age = 0; age <= 2; age++) {
                        parts.computeIfAbsent(Part.DECORATION, key -> new HashSet<>())
                                .add(Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.AGE, age));
                    }
                }
                default -> failures.add("Unsupported tree decorator: " + decorator.getClass().getName());
            }
        } catch (RuntimeException | LinkageError exception) {
            failures.add("Decorator " + decorator.getClass().getName() + ": " + describe(exception));
        }
    }

    private static String describe(Throwable exception) {
        return exception.getClass().getName() + ": " + exception.getMessage();
    }
}
