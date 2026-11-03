package com.holysweet.sweetbusiness.discovery.worldgen;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

import java.util.HashSet;
import java.util.Set;

public final class BlockStateProviderDiscoverer {

    private BlockStateProviderDiscoverer() {}

    public static Set<BlockState> discoverStates(RegistryAccess registries, BlockStateProvider provider) {
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        return discoverStates(ops, BlockStateProvider.CODEC.encodeStart(ops, provider).getOrThrow());
    }

    public static Set<BlockState> discoverStates(DynamicOps<JsonElement> ops, JsonElement definition) {
        Set<BlockState> states = new HashSet<>();
        collectStates(ops, definition, states);
        if (states.isEmpty()) throw new IllegalArgumentException("No declared block states found in provider");
        return Set.copyOf(states);
    }

    private static void collectStates(DynamicOps<JsonElement> ops, JsonElement element, Set<BlockState> states) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectStates(ops, child, states));
        } else if (element.isJsonObject()) {
            var object = element.getAsJsonObject();
            if (object.has("weight") && object.get("weight").getAsInt() <= 0) return;
            if (object.has("Name")) {
                states.add(BlockState.CODEC.parse(ops, object).getOrThrow());
            } else if (object.has("type")
                    && object.get("type").getAsString().equals("minecraft:randomized_int_state_provider")) {
                var values = IntProvider.CODEC.parse(ops, object.get("values")).getOrThrow();
                String propertyName = object.get("property").getAsString();
                for (BlockState state : discoverStates(ops, object.get("source"))) {
                    if (!(state.getBlock().getStateDefinition().getProperty(propertyName) instanceof IntegerProperty property)) {
                        throw new IllegalArgumentException("Missing integer property: " + propertyName);
                    }
                    property.getPossibleValues().stream()
                            .filter(value -> value >= values.getMinValue() && value <= values.getMaxValue())
                            .forEach(value -> states.add(state.setValue(property, value)));
                }
            } else {
                object.entrySet().forEach(entry -> collectStates(ops, entry.getValue(), states));
            }
        }
    }
}
