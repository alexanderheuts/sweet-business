package com.holysweet.sweetbusiness.discovery.simulation;

import com.holysweet.sweetbusiness.util.Statistics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

public final class EntityXpSimulator {

    private EntityXpSimulator() {}

    public static double simulateMedianXp(Mob mob, ServerLevel level, int iterations) {
        double[] samples = new double[iterations];
        for (int iteration = 0; iteration < samples.length; iteration++) {
            // No killer means no killer equipment or enchantment bonuses; no death or XP-drop events are fired.
            samples[iteration] = mob.getExperienceReward(level, null);
        }
        return Statistics.median(samples);
    }
}
