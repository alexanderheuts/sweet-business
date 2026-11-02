package com.holysweet.sweetbusiness.config;

import com.holysweet.sweetbusiness.util.Constants;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class Config {

    public static final ModConfigSpec SERVER_SPEC;

    public static final ModConfigSpec.DoubleValue TERRAIN_BASELINE;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BASE_ITEMS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BASE_TAGS;

    public static final ModConfigSpec.IntValue GLOBAL_DEFAULT_TIER;
    public static final ModConfigSpec.ConfigValue<String> GLOBAL_DEFAULT_TYPE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NAMESPACE_DEFAULTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RECIPE_TYPE_OVERRIDES;

    public static final ModConfigSpec.DoubleValue BASE_PROCESSING_BONUS;
    public static final ModConfigSpec.DoubleValue MANUAL_PROCESSING_BONUS;
    public static final Map<Integer, ModConfigSpec.DoubleValue> TIER_PROCESSING_BONUSES;

    public static final ModConfigSpec.DoubleValue CONTRACT_EXTRA_LINE_CHANCE;
    public static final ModConfigSpec.DoubleValue MOB_XP_VALUE_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue SPAWN_EGGS_ENABLE_FARMING;

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
                .defineInRange("globalDefaultTier", 1, 1, 5);

        GLOBAL_DEFAULT_TYPE = builder
                .comment("The default processing type: 'manual' or 'industrial'.",
                        "Recipe type overrides take precedence over namespace defaults, then global defaults.")
                .define("globalDefaultType", "industrial", Config::validateProcessingType);

        NAMESPACE_DEFAULTS = builder
                .comment("Baseline tech tiers and processing types for entire mods.",
                        "Format: 'modid:tier:manual' or 'modid:tier:industrial' (tier: 1-10)")
                .defineList("namespaceDefaults",
                        () -> List.of(
                                "minecraft:1:industrial"
                        ),
                        () -> "",
                        Config::validateNamespaceRule);

        RECIPE_TYPE_OVERRIDES = builder
                .comment("Specific tech tier and processing type overrides within a mod.",
                        "Format: 'modid:recipe_type:tier:manual' or 'modid:recipe_type:tier:industrial' (tier: 1-10)")
                .defineList("recipeTypeOverrides",
                        () -> List.of(
                                "minecraft:smithing:1:manual",
                                "minecraft:campfire_cooking:1:manual",
                                "minecraft:stonecutting:1:manual",
                                "minecraft:crafting:1:manual"
                        ),
                        () -> "",
                        Config::validateRecipeTypeRule);

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

        builder.push("contract_generation");
        CONTRACT_EXTRA_LINE_CHANCE = builder
                .comment("Percentage chance of an extra delivery line, rolled independently once per contract tier.")
                .defineInRange("extraLineChance", Constants.DEFAULT_CONTRACT_EXTRA_LINE_CHANCE, 0.0, 100.0);
        builder.pop();

        builder.push("mob_loot");
        SPAWN_EGGS_ENABLE_FARMING = builder
                .comment("Assume mobs with registered spawn eggs can be farmed industrially.",
                        "Disable if the modpack cannot use spawn eggs to configure mob spawners.")
                .define("spawnEggsEnableFarming", true);
        MOB_XP_VALUE_MULTIPLIER = builder
                .comment("Scales the mob loot value budget: effective kill XP * mob tier * this multiplier.")
                .defineInRange("xpValueMultiplier", Constants.DEFAULT_MOB_XP_VALUE_MULTIPLIER, 0.000001, Double.MAX_VALUE);
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

    private static boolean validateProcessingType(Object obj) {
        return "manual".equals(obj) || "industrial".equals(obj);
    }

    private static boolean validateNamespaceRule(Object obj) {
        return validateProcessingRule(obj, 1);
    }

    private static boolean validateRecipeTypeRule(Object obj) {
        return validateProcessingRule(obj, 2);
    }

    private static boolean validateProcessingRule(Object obj, int tierIndex) {
        if (!(obj instanceof String entry)) return false;

        String[] parts = entry.split(":", -1);
        if (parts.length != tierIndex + 2 || !validateProcessingType(parts[tierIndex + 1])) return false;
        if (!parts[0].matches("[a-z0-9_.-]+")) return false;
        if (tierIndex == 2 && (parts[1].isEmpty() || ResourceLocation.tryParse(parts[0] + ":" + parts[1]) == null)) return false;

        try {
            int tier = Integer.parseInt(parts[tierIndex]);
            return tier >= 1 && tier <= 10;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
