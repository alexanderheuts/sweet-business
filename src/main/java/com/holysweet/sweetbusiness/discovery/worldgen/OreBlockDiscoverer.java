package com.holysweet.sweetbusiness.discovery.worldgen;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OreBlockDiscoverer {

    private OreBlockDiscoverer() {}

    public record OreProperties(int veinSize, List<BlockState> targetStates) {
        public OreProperties {
            targetStates = List.copyOf(targetStates);
        }
    }

    public static Map<PlacedFeature, OreProperties> discoverBlocks(WorldGenDiscoverer.DiscoveryResult worldgen) {
        Map<PlacedFeature, OreProperties> ores = new HashMap<>();
        for (PlacedFeature feature : worldgen.featureBiomes().keySet()) {
            if (feature.feature().value().config() instanceof OreConfiguration config) {
                List<BlockState> states = config.targetStates.stream().map(target -> target.state).toList();
                ores.put(feature, new OreProperties(config.size, states));
            }
        }
        return Map.copyOf(ores);
    }
}
