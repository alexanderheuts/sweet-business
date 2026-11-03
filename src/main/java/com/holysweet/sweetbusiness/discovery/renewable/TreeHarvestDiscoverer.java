package com.holysweet.sweetbusiness.discovery.renewable;

import com.holysweet.sweetbusiness.discovery.simulation.BlockLootSimulator;
import com.holysweet.sweetbusiness.util.Constants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class TreeHarvestDiscoverer {

    private TreeHarvestDiscoverer() {}

    public enum HarvestMode { NORMAL, SHEARS }

    public record HarvestDrops(Map<HarvestMode, Map<Item, Double>> expectedDrops) {
        public HarvestDrops {
            Map<HarvestMode, Map<Item, Double>> snapshot = new EnumMap<>(HarvestMode.class);
            expectedDrops.forEach((mode, drops) -> snapshot.put(mode, Map.copyOf(drops)));
            expectedDrops = Map.copyOf(snapshot);
        }
    }

    public record DiscoveryResult(Map<BlockState, HarvestDrops> harvests,
                                  Map<BlockState, Map<HarvestMode, String>> failures) {
        public DiscoveryResult {
            harvests = Map.copyOf(harvests);
            Map<BlockState, Map<HarvestMode, String>> snapshot = new HashMap<>();
            failures.forEach((state, reasons) -> snapshot.put(state, Map.copyOf(reasons)));
            failures = Map.copyOf(snapshot);
        }
    }

    public static DiscoveryResult discoverDrops(ServerLevel level, Set<BlockState> states) {
        Map<BlockState, HarvestDrops> harvests = new HashMap<>();
        Map<BlockState, Map<HarvestMode, String>> failures = new HashMap<>();
        for (BlockState state : states) {
            Map<HarvestMode, Map<Item, Double>> drops = new EnumMap<>(HarvestMode.class);
            Map<HarvestMode, String> reasons = new EnumMap<>(HarvestMode.class);
            boolean leaves = state.getBlock() instanceof LeavesBlock || state.is(BlockTags.LEAVES);
            discoverMode(level, state, HarvestMode.NORMAL,
                    leaves ? ItemStack.EMPTY : new ItemStack(Items.NETHERITE_AXE), drops, reasons);
            if (leaves) discoverMode(level, state, HarvestMode.SHEARS, new ItemStack(Items.SHEARS), drops, reasons);
            harvests.put(state, new HarvestDrops(drops));
            if (!reasons.isEmpty()) failures.put(state, reasons);
        }
        return new DiscoveryResult(harvests, failures);
    }

    private static void discoverMode(ServerLevel level, BlockState state, HarvestMode mode, ItemStack tool,
                                      Map<HarvestMode, Map<Item, Double>> drops, Map<HarvestMode, String> failures) {
        try {
            var table = level.getServer().reloadableRegistries().getLootTable(state.getBlock().getLootTable());
            drops.put(mode, BlockLootSimulator.simulate(level, table, state, tool, Constants.TREE_LOOT_SIMULATION_ITERATIONS));
        } catch (RuntimeException | LinkageError exception) {
            failures.put(mode, exception.getClass().getName() + ": " + exception.getMessage());
        }
    }
}
