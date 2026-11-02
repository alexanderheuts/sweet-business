package com.holysweet.sweetbusiness.valuation.discovery;

import com.holysweet.sweetbusiness.util.Constants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Vex;
import net.neoforged.neoforge.common.Tags;

import java.util.HashMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class EntityDiscoverer {

    private EntityDiscoverer() {}

    public record EntityProperties(boolean hostile, boolean boss, boolean spawnsInOverworld,
                                   boolean flying, boolean swimming, double maxHealth, double medianXp) {}

    public record DiscoveryResult(Map<EntityType<?>, EntityProperties> properties,
                                  Map<EntityType<?>, String> failures) {
        public DiscoveryResult {
            properties = Map.copyOf(properties);
            failures = Map.copyOf(failures);
        }
    }

    public static DiscoveryResult discoverEntities(MinecraftServer server) {
        Set<EntityType<?>> overworldSpawns = findOverworldSpawns(server);
        Map<EntityType<?>, EntityProperties> properties = new HashMap<>();
        Map<EntityType<?>, String> failures = new HashMap<>();

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type == EntityType.PLAYER) continue;
            if (!DefaultAttributes.hasSupplier(type)) {
                if (type.getCategory() != MobCategory.MISC) {
                    failures.put(type, "No registered default attributes");
                }
                continue;
            }

            try {
                // Construct for inspection only; never add the entity to the level or finalize its spawn.
                var entity = type.create(server.overworld());
                if (entity == null) {
                    failures.put(type, "Entity factory returned null");
                    continue;
                }
                if (!(entity instanceof Mob mob)) continue;

                properties.put(type, new EntityProperties(mob instanceof Enemy,
                        type.is(Tags.EntityTypes.BOSSES), overworldSpawns.contains(type),
                        isFlying(mob), isSwimming(mob), mob.getAttributeBaseValue(Attributes.MAX_HEALTH),
                        simulateMedianXp(mob, server.overworld())));
            } catch (RuntimeException | LinkageError exception) {
                failures.put(type, exception.getClass().getName() + ": " + exception.getMessage());
            }
        }
        return new DiscoveryResult(properties, failures);
    }

    private static double simulateMedianXp(Mob mob, ServerLevel level) {
        int[] samples = new int[Constants.MOB_XP_SIMULATION_ITERATIONS];
        for (int iteration = 0; iteration < samples.length; iteration++) {
            // No killer means no killer equipment or enchantment bonuses; no death or XP-drop events are fired.
            samples[iteration] = mob.getExperienceReward(level, null);
        }
        Arrays.sort(samples);
        int lowerMiddle = (samples.length - 1) / 2;
        int upperMiddle = samples.length / 2;
        return (samples[lowerMiddle] + (double) samples[upperMiddle]) / 2.0;
    }

    private static Set<EntityType<?>> findOverworldSpawns(MinecraftServer server) {
        Set<EntityType<?>> types = new HashSet<>();
        var biomes = server.overworld().getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
        for (var biome : biomes) {
            for (MobCategory category : MobCategory.values()) {
                for (var spawn : biome.value().getMobSettings().getMobs(category).unwrap()) {
                    types.add(spawn.type);
                }
            }
        }
        return types;
    }

    private static boolean isFlying(Mob mob) {
        return mob instanceof FlyingMob
                || mob instanceof FlyingAnimal
                || mob.getNavigation() instanceof FlyingPathNavigation
                || mob.getMoveControl() instanceof FlyingMoveControl
                // These vanilla flyers use custom movement without the standard flight markers.
                || mob instanceof Blaze
                || mob instanceof Vex
                || mob instanceof EnderDragon;
    }

    private static boolean isSwimming(Mob mob) {
        return mob instanceof WaterAnimal
                || mob.getNavigation() instanceof WaterBoundPathNavigation
                || mob.getNavigation() instanceof AmphibiousPathNavigation
                || mob.getMoveControl() instanceof SmoothSwimmingMoveControl
                // Drowned start on ground navigation and switch to their water navigator later.
                || mob instanceof Drowned;
    }
}
