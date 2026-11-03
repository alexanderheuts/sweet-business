package com.holysweet.sweetbusiness.evaluation.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.Tags;

public final class TierResolver {

    private TierResolver() {
    }

    /**
     * Determines the baseline tech level required to harvest a block
     * based on standard vanilla and NeoForge tool tags.
     */
    public static int getBlockTechLevel(BlockState state) {
        if (state.is(Tags.Blocks.NEEDS_NETHERITE_TOOL)) {
            return 4;
        } else if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            return 3;
        } else if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
            return 2;
        }

        // Default for wood, stone, dirt, and untagged blocks
        return 1;
    }

}