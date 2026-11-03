package com.holysweet.sweetbusiness.config;

import com.holysweet.sweetbusiness.util.Constants;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ValuationConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue TERRAIN_BASELINE;
    public static final ModConfigSpec.DoubleValue CROP_BASELINE;
    public static final ModConfigSpec.DoubleValue SAPLING_BASELINE;
    public static final ModConfigSpec.DoubleValue TREE_WOOD_BASELINE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BASE_ITEMS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BASE_TAGS;
    public static final ModConfigSpec.DoubleValue BASE_PROCESSING_BONUS;
    public static final ModConfigSpec.DoubleValue MANUAL_PROCESSING_BONUS;
    public static final Map<Integer, ModConfigSpec.DoubleValue> TIER_PROCESSING_BONUSES;
    public static final ModConfigSpec.DoubleValue MOB_XP_VALUE_MULTIPLIER;

    private ValuationConfig() {}

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment(
                "Economy and Valuation Engine Settings",
                "WARNING: Changing these configurations can seriously destabilize fair economy balancing.",
                "Increasing the value of boring blocks, or specific items like diamonds, cascades all the way through the recipe tree.",
                "Only alter these values if you fully understand the mathematical impact."
        ).push("valuation");

        TERRAIN_BASELINE = builder
                .comment("The baseline point value assigned to common 'boring' terrain blocks.",
                        "Keep this low so dirt/stone doesn't rival the value of ores.")
                .defineInRange("terrainBaseline", 0.002, 0.000001, 1.0);

        CROP_BASELINE = builder
                .comment("Wheat-seed reference value for naturally sourced crop planting items and harvests.",
                        "Applies per item, independent of harvest yield. Explicit item and tag overrides take precedence.")
                .defineInRange("cropBaseline", Constants.DEFAULT_CROP_BASELINE, 0.000001, (double) Float.MAX_VALUE);

        SAPLING_BASELINE = builder
                .comment("Per-item baseline for naturally sourced saplings and tree fruit.")
                .defineInRange("saplingBaseline", Constants.DEFAULT_SAPLING_BASELINE, 0.000001, (double) Float.MAX_VALUE);
        TREE_WOOD_BASELINE = builder
                .comment("Per-item baseline for naturally sourced tree logs, sheared leaves, and roots.",
                        "Recipe-derived trees scale woody outputs by treeWoodBaseline / saplingBaseline.")
                .defineInRange("treeWoodBaseline", Constants.DEFAULT_TREE_WOOD_BASELINE, 0.000001, (double) Float.MAX_VALUE);

        BASE_ITEMS = builder
                .comment("Define base individual items, starting values, and optional tech levels.",
                        "Format: 'namespace:item_id=value;techLevel' (Tech level is optional, defaults to 1)")
                .defineList("baseItems",
                        () -> List.of(
                                // Earth & Minerals
                                "minecraft:obsidian=0.5",
                                "minecraft:amethyst_shard=1.0;2",
                                "minecraft:snowball=0.1",
                                "minecraft:glowstone_dust=0.5;2",

                                // Dimension Terrain
                                "minecraft:netherrack=0.1",
                                "minecraft:end_stone=0.1",
                                "minecraft:soul_soil=0.1",
                                "minecraft:basalt=0.1",

                                // Agriculture & Forage
                                "minecraft:kelp=0.1",
                                "minecraft:sugar_cane=0.1",
                                "minecraft:bamboo=0.1",
                                "minecraft:cocoa_beans=0.1",
                                "minecraft:sweet_berries=0.1",
                                "minecraft:pumpkin_seeds=0.1",
                                "minecraft:melon_seeds=0.1",
                                "minecraft:beetroot_seeds=0.1",
                                "minecraft:red_mushroom=0.1",
                                "minecraft:brown_mushroom=0.1",
                                "minecraft:carrot=0.1",
                                "minecraft:potato=0.1",
                                "minecraft:nether_wart=0.1",
                                "minecraft:apple=0.1",
                                "minecraft:cactus=0.1",
                                "minecraft:chorus_fruit=0.5;3",

                                // Animal & Mob Drops
                                "minecraft:egg=0.5",
                                "minecraft:honeycomb=0.5",
                                "minecraft:feather=0.5;2",
                                "minecraft:leather=0.5;2",
                                "minecraft:bone=0.5;2",
                                "minecraft:rotten_flesh=0.5;2",
                                "minecraft:string=0.5;2",
                                "minecraft:spider_eye=0.5;2",
                                "minecraft:gunpowder=0.5;2",
                                "minecraft:slime_ball=0.5;2",
                                "minecraft:ink_sac=0.5;2",
                                "minecraft:glow_ink_sac=0.5;2",

                                // Gated & High-Tech Drops
                                "minecraft:phantom_membrane=2.0;3",
                                "minecraft:prismarine_shard=2.0;3",
                                "minecraft:prismarine_crystals=2.0;3",
                                "minecraft:ender_pearl=3.0;3",
                                "minecraft:blaze_rod=3.0;3",
                                "minecraft:breeze_rod=3.0;2",
                                "minecraft:shulker_shell=5.0;3",
                                "minecraft:ghast_tear=5.0;4",
                                "minecraft:nether_star=50.0;5"
                        ),
                        () -> "",
                        ValuationConfig::validateAnchorEntry);

        BASE_TAGS = builder
                .comment("Define base tags, starting values, and optional tech levels.",
                        "Format: 'namespace:tag_id=value;techLevel' (Do NOT use the # prefix)")
                .defineList("baseTags",
                        () -> List.of(
                                "minecraft:logs=0.1"
                        ),
                        () -> "",
                        ValuationConfig::validateAnchorEntry);

        builder.pop();

        builder.comment("Percentage markups added together for each recipe's processing step.")
                .push("processing_tax");

        BASE_PROCESSING_BONUS = builder
                .comment("Markup applied to every processing step. A value of 2 means 2%.")
                .defineInRange("baseProcessingBonus", 2.0, 0.0, Double.MAX_VALUE);

        MANUAL_PROCESSING_BONUS = builder
                .comment("Additional markup when the current recipe type is manual.",
                        "Manual ingredients alone do not trigger this markup.")
                .defineInRange("manualProcessingBonus", 10.0, 0.0, Double.MAX_VALUE);

        builder.comment("Additional markup by the current recipe type's tier, independent of ingredient tiers.")
                .push("tierBonuses");
        Map<Integer, ModConfigSpec.DoubleValue> tierBonuses = new HashMap<>();
        for (int tier = 1; tier <= 5; tier++) {
            tierBonuses.put(tier, builder.defineInRange("tier" + tier, (double) tier, 0.0, Double.MAX_VALUE));
        }
        TIER_PROCESSING_BONUSES = Map.copyOf(tierBonuses);
        builder.pop();
        builder.pop();

        builder.push("mob_loot");
        MOB_XP_VALUE_MULTIPLIER = builder
                .comment("Scales the mob loot value budget: effective kill XP * mob tier * this multiplier.")
                .defineInRange("xpValueMultiplier", Constants.DEFAULT_MOB_XP_VALUE_MULTIPLIER, 0.000001, Double.MAX_VALUE);
        builder.pop();

        SPEC = builder.build();
    }

    private static boolean validateAnchorEntry(Object obj) {
        if (obj instanceof String str) {
            String[] parts = str.split("=");
            if (parts.length == 2) {
                try {
                    String[] valParts = parts[1].split(";");
                    Float.parseFloat(valParts[0]);
                    if (valParts.length > 1) {
                        Integer.parseInt(valParts[1]);
                    }
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        return false;
    }

}
