package com.holysweet.sweetbusiness.evaluation.worldgen;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;

public final class TerrainClassifier {

    private TerrainClassifier() {}

    /**
     * Determines if a block is structural terrain rather than a targeted resource,
     * ensuring it receives a baseline value instead of dynamic scarcity math.
     */
    public static boolean isBoringTerrain(Block block) {
        var state = block.defaultBlockState();

        // Catch infested blocks (Silverfish) so they don't get priced as rare gems
        if (BuiltInRegistries.BLOCK.getKey(block).getPath().contains("infested")) {
            return true;
        }

        return state.is(BlockTags.DIRT)
                || state.is(Tags.Blocks.COBBLESTONES)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(BlockTags.SAND)
                || state.is(BlockTags.TERRACOTTA)
                || block == Blocks.GRAVEL
                || block == Blocks.TUFF
                || block == Blocks.CALCITE
                || block == Blocks.SOUL_SAND
                || block == Blocks.SOUL_SOIL
                || block == Blocks.MAGMA_BLOCK
                || block == Blocks.CLAY
                || block == Blocks.MUD;
    }
}
