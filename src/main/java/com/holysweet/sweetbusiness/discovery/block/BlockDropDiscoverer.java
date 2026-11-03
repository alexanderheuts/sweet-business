package com.holysweet.sweetbusiness.discovery.block;

import com.holysweet.sweetbusiness.discovery.simulation.BlockLootSimulator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

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

        return BlockLootSimulator.simulate(level, table, state, new ItemStack(Items.NETHERITE_PICKAXE), ITERATIONS);
    }
}
