package com.holysweet.sweetbusiness.config;

import com.holysweet.sweetbusiness.util.Constants;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue CONTRACT_EXTRA_LINE_CHANCE;
    public static final ModConfigSpec.DoubleValue CONTRACT_DROP_CHANCE;
    public static final ModConfigSpec.BooleanValue SPAWN_EGGS_ENABLE_FARMING;

    private ServerConfig() {}

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("contract_generation");
        CONTRACT_EXTRA_LINE_CHANCE = builder
                .comment("Percentage chance of an extra delivery line, rolled independently once per contract tier.")
                .defineInRange("extraLineChance", Constants.DEFAULT_CONTRACT_EXTRA_LINE_CHANCE, 0.0, 100.0);
        builder.pop();

        builder.push("mob_loot");
        CONTRACT_DROP_CHANCE = builder
                .comment("Percentage chance for an Enemy killed by a player to drop a contract.",
                        "Requires eligible items in the killing player's CraftableSet. Looting has no effect.")
                .defineInRange("contractDropChance", 1.0, 0.0, 100.0);
        SPAWN_EGGS_ENABLE_FARMING = builder
                .comment("Assume mobs with registered spawn eggs can be farmed industrially.",
                        "Disable if the modpack cannot use spawn eggs to configure mob spawners.")
                .define("spawnEggsEnableFarming", true);
        builder.pop();

        SPEC = builder.build();
    }
}
