package com.holysweet.sweetbusiness.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

public class Config {

    public static final ModConfigSpec SERVER_SPEC;

    public static final ModConfigSpec.DoubleValue TERRAIN_BASELINE;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BASE_ITEMS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BASE_TAGS;

    public static final ModConfigSpec.IntValue GLOBAL_DEFAULT_TIER;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NAMESPACE_DEFAULTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RECIPE_TYPE_OVERRIDES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MANUAL_RECIPE_TYPES;

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
                                "minecraft:wheat_seeds=0.1",
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
                        Config::validateAnchorEntry);

        BASE_TAGS = builder
                .comment("Define base tags, starting values, and optional tech levels.",
                        "Format: 'namespace:tag_id=value;techLevel' (Do NOT use the # prefix)")
                .defineList("baseTags",
                        () -> List.of(
                                "c:cobblestones=0.1",
                                "minecraft:logs=0.1"
                        ),
                        () -> "",
                        Config::validateAnchorEntry);

        builder.pop();

        builder.comment(
                "Processing Tier and Supply Chain Settings",
                "These settings determine the baseline technological tier of recipes and whether they require manual labor."
        ).push("processing_tiers");

        GLOBAL_DEFAULT_TIER = builder
                .comment("The default tech tier for any recipe type not explicitly defined below.")
                .defineInRange("globalDefaultTier", 1, 1, 10);

        NAMESPACE_DEFAULTS = builder
                .comment("Baseline tech tiers for entire mods.",
                        "Format: 'modid:tier'")
                .defineList("namespaceDefaults",
                        () -> List.of(
                                "minecraft:1"
                        ),
                        () -> "",
                        Config::validateNamespaceTier);

        RECIPE_TYPE_OVERRIDES = builder
                .comment("Specific overrides for high-end or primitive machines within a mod.",
                        "Format: 'modid:recipe_type:tier'")
                .defineList("recipeTypeOverrides",
                        () -> List.of(
                                "minecraft:smithing:1"
                        ),
                        () -> "",
                        Config::validateRecipeTypeTier);

        MANUAL_RECIPE_TYPES = builder
                .comment("Recipe types that require direct player interaction.",
                        "The engine uses this to apply manual labor taxes and cap contract volumes.",
                        "Format: 'modid:recipe_type'")
                .defineList("manualRecipeTypes",
                        () -> List.of(
                                "minecraft:smithing",
                                "minecraft:campfire_cooking",
                                "minecraft:stonecutting",
                                "minecraft:crafting"
                        ),
                        () -> "",
                        Config::validateRecipeType);

        builder.pop();
        SERVER_SPEC = builder.build();
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

    private static boolean validateNamespaceTier(Object obj) {
        if (obj instanceof String str) {
            String[] parts = str.split(":");
            if (parts.length == 2) {
                try {
                    Integer.parseInt(parts[1]);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean validateRecipeTypeTier(Object obj) {
        if (obj instanceof String str) {
            String[] parts = str.split(":");
            if (parts.length == 3) {
                try {
                    Integer.parseInt(parts[2]);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean validateRecipeType(Object obj) {
        if (obj instanceof String str) {
            return str.split(":").length == 2;
        }
        return false;
    }
}