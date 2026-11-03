package com.holysweet.sweetbusiness.discovery.block;

import com.holysweet.sweetbusiness.util.Constants;
import com.holysweet.sweetbusiness.discovery.loot.LootSimulator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public final class GrassDropDiscoverer {

    private GrassDropDiscoverer() {}

    public record DiscoveryResult(Map<Block, Map<Item, Double>> expectedDrops, Map<Block, String> failures) {
        public DiscoveryResult {
            Map<Block, Map<Item, Double>> snapshot = new HashMap<>();
            expectedDrops.forEach((block, drops) -> snapshot.put(block, Map.copyOf(drops)));
            expectedDrops = Map.copyOf(snapshot);
            failures = Map.copyOf(failures);
        }
    }

    public static DiscoveryResult discoverDrops(ServerLevel level) {
        Map<Block, Map<Item, Double>> expectedDrops = new HashMap<>();
        Map<Block, String> failures = new HashMap<>();

        for (Block block : BuiltInRegistries.BLOCK) {
            if (!isGrass(block)) continue;

            try {
                expectedDrops.put(block, simulateDrops(level, block));
            } catch (RuntimeException | LinkageError exception) {
                failures.put(block, exception.getClass().getName() + ": " + exception.getMessage());
            }
        }
        return new DiscoveryResult(expectedDrops, failures);
    }

    private static boolean isGrass(Block block) {
        return block instanceof TallGrassBlock || block == Blocks.TALL_GRASS || block == Blocks.LARGE_FERN;
    }

    private static Map<Item, Double> simulateDrops(ServerLevel level, Block block) {
        // Double-height plants use their lower state so each plant is sampled once.
        BlockState state = block.defaultBlockState();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(block.getLootTable());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withLuck(0.0F)
                .create(LootContextParamSets.BLOCK);
        return LootSimulator.getInstance().simulate(table, params, Constants.GRASS_LOOT_SIMULATION_ITERATIONS);
    }
}
