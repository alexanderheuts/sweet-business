package com.holysweet.sweetbusiness.valuation.discovery;

import com.holysweet.sweetbusiness.util.Constants;
import com.holysweet.sweetbusiness.util.LootSimulator;
import com.holysweet.sweetbusiness.valuation.EntityTierEvaluator.EntityTier;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EntityLootDiscoverer {

    private EntityLootDiscoverer() {}

    public record DiscoveryResult(Map<EntityType<?>, Map<Item, Double>> expectedDrops,
                                  Map<EntityType<?>, String> failures) {
        public DiscoveryResult {
            Map<EntityType<?>, Map<Item, Double>> snapshot = new HashMap<>();
            expectedDrops.forEach((type, drops) -> snapshot.put(type, Map.copyOf(drops)));
            expectedDrops = Map.copyOf(snapshot);
            failures = Map.copyOf(failures);
        }
    }

    public static DiscoveryResult discoverLoot(MinecraftServer server, Map<EntityType<?>, EntityTier> entityTiers) {
        ServerLevel level = server.overworld();
        Map<EntityType<?>, Map<Item, Double>> expectedDrops = new HashMap<>();
        Map<EntityType<?>, String> failures = new HashMap<>();
        UUID killerId = UUID.nameUUIDFromBytes("sweetbusiness:loot_simulator".getBytes(StandardCharsets.UTF_8));
        FakePlayer killer = new FakePlayer(level, new GameProfile(killerId, "[SweetBusiness]"));

        for (var entry : entityTiers.entrySet()) {
            EntityType<?> type = entry.getKey();
            EntityTier tier = entry.getValue();
            if (!tier.hasLootTable()) continue;

            try {
                if (!(type.create(level) instanceof Mob mob)) {
                    failures.put(type, "Could not construct mob for loot simulation");
                    continue;
                }
                LootParams params = createLootParams(level, mob, killer);
                expectedDrops.put(type, LootSimulator.getInstance().simulate(
                        tier.lootTable(), params, Constants.MOB_LOOT_SIMULATION_ITERATIONS));
            } catch (RuntimeException | LinkageError exception) {
                failures.put(type, exception.getClass().getName() + ": " + exception.getMessage());
            }
        }
        return new DiscoveryResult(expectedDrops, failures);
    }

    private static LootParams createLootParams(ServerLevel level, Mob mob, FakePlayer killer) {
        // An unequipped, unspawned player enables player-kill conditions without Looting or Luck bonuses.
        return new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(killer))
                .withParameter(LootContextParams.ATTACKING_ENTITY, killer)
                .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .withLuck(0.0F)
                .create(LootContextParamSets.ENTITY);
    }
}
