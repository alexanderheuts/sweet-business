package com.holysweet.sweetbusiness.discovery.simulation;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public final class EntityLootSimulator {

    private final ServerLevel level;
    private final FakePlayer killer;

    public EntityLootSimulator(ServerLevel level) {
        this.level = level;
        UUID killerId = UUID.nameUUIDFromBytes("sweetbusiness:loot_simulator".getBytes(StandardCharsets.UTF_8));
        killer = new FakePlayer(level, new GameProfile(killerId, "[SweetBusiness]"));
    }

    public Map<Item, Double> simulate(Mob mob, LootTable table, int iterations) {
        // An unequipped, unspawned player enables player-kill conditions without Looting or Luck bonuses.
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(killer))
                .withParameter(LootContextParams.ATTACKING_ENTITY, killer)
                .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .withLuck(0.0F)
                .create(LootContextParamSets.ENTITY);
        return LootSimulator.getInstance().simulate(table, params, iterations);
    }
}
