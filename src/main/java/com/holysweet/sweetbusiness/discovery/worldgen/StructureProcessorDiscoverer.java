package com.holysweet.sweetbusiness.discovery.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

public final class StructureProcessorDiscoverer {

    private StructureProcessorDiscoverer() {}

    public record DiscoveryResult(Set<BlockState> states, Set<String> failures) {
        public DiscoveryResult {
            states = Set.copyOf(states);
            failures = Set.copyOf(failures);
        }
    }

    private enum Match { NEVER, POSSIBLE, ALWAYS }

    public static DiscoveryResult discoverOutputs(Set<BlockState> states, JsonElement definition,
                                                  DynamicOps<JsonElement> ops) {
        Set<String> failures = new HashSet<>();
        JsonElement processors = definition.isJsonObject() ? definition.getAsJsonObject().get("processors") : definition;
        Set<BlockState> outputs = new HashSet<>(states);
        for (JsonElement processor : processors.getAsJsonArray()) {
            try {
                outputs = applyProcessor(outputs, processor.getAsJsonObject(), ops, failures);
            } catch (RuntimeException | LinkageError exception) {
                failures.add(exception.getClass().getName() + ": " + exception.getMessage());
            }
        }
        return new DiscoveryResult(outputs, failures);
    }

    private static Set<BlockState> applyProcessor(Set<BlockState> states, JsonObject processor,
                                                  DynamicOps<JsonElement> ops, Set<String> failures) {
        String type = processor.get("processor_type").getAsString();
        return switch (type) {
            case "minecraft:rule" -> applyRules(states, processor, ops, failures);
            case "minecraft:capped" -> {
                // A cap can affect only some block instances, so retain the unchanged possibility.
                Set<BlockState> outputs = new HashSet<>(states);
                JsonElement limit = processor.get("limit");
                if (IntProvider.CODEC.parse(ops, limit).getOrThrow().getMaxValue() > 0) {
                    outputs.addAll(applyProcessor(states, processor.getAsJsonObject("delegate"), ops, failures));
                }
                yield outputs;
            }
            case "minecraft:nop", "minecraft:gravity", "minecraft:protected_blocks" -> states;
            case "minecraft:block_rot" -> {
                if (processor.get("integrity").getAsDouble() <= 0) {
                    if (!processor.has("rottable_blocks")) yield Set.of();
                    var rottable = RegistryCodecs.homogeneousList(Registries.BLOCK)
                            .parse(ops, processor.get("rottable_blocks")).getOrThrow();
                    Set<BlockState> remaining = new HashSet<>(states);
                    remaining.removeIf(state -> state.is(rottable));
                    yield remaining;
                }
                yield states;
            }
            default -> {
                failures.add("Unsupported structure processor: " + type);
                yield states;
            }
        };
    }

    private static Set<BlockState> applyRules(Set<BlockState> states, JsonObject processor,
                                              DynamicOps<JsonElement> ops, Set<String> failures) {
        Set<BlockState> outputs = new HashSet<>();
        for (BlockState state : states) {
            boolean unchanged = true;
            for (JsonElement element : processor.getAsJsonArray("rules")) {
                JsonObject rule = element.getAsJsonObject();
                Match input = matchInput(state, rule.getAsJsonObject("input_predicate"), ops, failures);
                if (input == Match.NEVER) continue;
                Match location = matchLocation(rule.getAsJsonObject("location_predicate"), failures);
                if (location == Match.NEVER) continue;
                if (rule.has("position_predicate") && !rule.getAsJsonObject("position_predicate")
                        .get("predicate_type").getAsString().equals("minecraft:always_true")) {
                    failures.add("Unsupported structure rule position predicate: " + rule.get("position_predicate"));
                    continue;
                }
                outputs.add(BlockState.CODEC.parse(ops, rule.get("output_state")).getOrThrow());
                // Rules select the first match; guaranteed matches exclude later rules and the original state.
                if (input == Match.ALWAYS && location == Match.ALWAYS) {
                    unchanged = false;
                    break;
                }
            }
            if (unchanged) outputs.add(state);
        }
        return outputs;
    }

    private static Match matchInput(BlockState state, JsonObject predicate, DynamicOps<JsonElement> ops,
                                     Set<String> failures) {
        String type = predicate.get("predicate_type").getAsString();
        boolean matches = switch (type) {
            case "minecraft:always_true" -> true;
            case "minecraft:block_match", "minecraft:random_block_match" -> state.is(BuiltInRegistries.BLOCK
                    .getOptional(ResourceLocation.parse(predicate.get("block").getAsString())).orElseThrow());
            case "minecraft:blockstate_match", "minecraft:random_blockstate_match" ->
                    state.equals(BlockState.CODEC.parse(ops, predicate.get("block_state")).getOrThrow());
            case "minecraft:tag_match" -> state.is(TagKey.create(Registries.BLOCK,
                    ResourceLocation.parse(predicate.get("tag").getAsString())));
            default -> {
                failures.add("Unsupported structure rule input predicate: " + type);
                yield false;
            }
        };
        if (!matches) return Match.NEVER;
        return probability(predicate);
    }

    private static Match matchLocation(JsonObject predicate, Set<String> failures) {
        String type = predicate.get("predicate_type").getAsString();
        return switch (type) {
            case "minecraft:always_true" -> Match.ALWAYS;
            case "minecraft:block_match", "minecraft:blockstate_match", "minecraft:tag_match",
                 "minecraft:random_block_match", "minecraft:random_blockstate_match" ->
                    probability(predicate) == Match.NEVER ? Match.NEVER : Match.POSSIBLE;
            default -> {
                failures.add("Unsupported structure rule location predicate: " + type);
                yield Match.NEVER;
            }
        };
    }

    private static Match probability(JsonObject predicate) {
        if (!predicate.has("probability")) return Match.ALWAYS;
        double probability = predicate.get("probability").getAsDouble();
        if (probability <= 0) return Match.NEVER;
        return probability >= 1 ? Match.ALWAYS : Match.POSSIBLE;
    }
}
