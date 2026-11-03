package com.holysweet.sweetbusiness.config;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class TierConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue GLOBAL_DEFAULT_TIER;
    public static final ModConfigSpec.ConfigValue<String> GLOBAL_DEFAULT_TYPE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NAMESPACE_DEFAULTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RECIPE_TYPE_OVERRIDES;

    private TierConfig() {}

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

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
                .define("globalDefaultType", "industrial", TierConfig::validateProcessingType);

        NAMESPACE_DEFAULTS = builder
                .comment("Baseline tech tiers and processing types for entire mods.",
                        "Format: 'modid:tier:manual' or 'modid:tier:industrial' (tier: 1-5)")
                .defineList("namespaceDefaults",
                        () -> List.of(
                                "minecraft:1:industrial"
                        ),
                        () -> "",
                        TierConfig::validateNamespaceRule);

        RECIPE_TYPE_OVERRIDES = builder
                .comment("Specific tech tier and processing type overrides within a mod.",
                        "Format: 'modid:recipe_type:tier:manual' or 'modid:recipe_type:tier:industrial' (tier: 1-5)")
                .defineList("recipeTypeOverrides",
                        () -> List.of(
                                "minecraft:smithing:1:manual",
                                "minecraft:campfire_cooking:1:manual",
                                "minecraft:stonecutting:1:manual",
                                "minecraft:crafting:1:manual"
                        ),
                        () -> "",
                        TierConfig::validateRecipeTypeRule);

        builder.pop();
        SPEC = builder.build();
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
            return tier >= 1 && tier <= 5;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
