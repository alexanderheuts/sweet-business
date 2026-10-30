package com.holysweet.sweetbusiness.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

public class Config {

    public static final ModConfigSpec SERVER_SPEC;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BASE_ITEMS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BASE_TAGS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Economy and Valuation Engine Settings").push("valuation");

        BASE_ITEMS = builder
                .comment("Define base individual items and their starting values.",
                        "Format: 'namespace:item_id=value'")
                .defineList("baseItems",
                        () -> List.of(
                                "minecraft:clay_ball=1.0"
                        ),
                        () -> "",
                        Config::validateAnchorEntry);

        BASE_TAGS = builder
                .comment("Define base tags and their starting values. All items in the tag receive this value.",
                        "Format: 'namespace:tag_id=value' (Do NOT use the # prefix)")
                .defineList("baseTags",
                        () -> List.of(
                                "minecraft:logs=2.0",
                                "minecraft:dirt=1.0",
                                "c:cobblestones=1.0",
                                "c:ores=15.0"
                        ),
                        () -> "",
                        Config::validateAnchorEntry);

        builder.pop();
        SERVER_SPEC = builder.build();
    }

    private static boolean validateAnchorEntry(Object obj) {
        if (obj instanceof String str) {
            String[] parts = str.split("=");
            if (parts.length == 2) {
                try {
                    Float.parseFloat(parts[1]); // Validate the value is a number
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        return false;
    }
}