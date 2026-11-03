package com.holysweet.sweetbusiness.discovery.simulation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public final class BlockLootSimulator {

    private BlockLootSimulator() {}

    public static Map<Item, Double> simulate(ServerLevel level, LootTable table, BlockState state,
                                              ItemStack tool, int iterations) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.TOOL, tool)
                .withLuck(0.0F)
                .create(LootContextParamSets.BLOCK);
        return LootSimulator.getInstance().simulate(table, params, iterations);
    }
}
