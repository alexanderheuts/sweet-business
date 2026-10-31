package com.holysweet.sweetbusiness.valuation.discovery;

import com.holysweet.sweetbusiness.util.LootSimulator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class BlockDropDiscoverer {

    private static final int ITERATIONS = 100;

    /**
     * Discovers the drops for a block, simulating a baseline Netherite Pickaxe.
     */
    public static Map<Item, Double> getAverageDrops(ServerLevel level, Block block) {
        BlockState state = block.defaultBlockState();

        // Fetch the resolved LootTable from the server's loot data manager
        LootTable table = level.getServer().reloadableRegistries().getLootTable(block.getLootTable());

        // Build the specific context required by Block loot tables
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.NETHERITE_PICKAXE))
                .create(LootContextParamSets.BLOCK);

        // Run the generic simulator and return result
        return LootSimulator.getInstance().simulate(table, params, ITERATIONS);
    }
}