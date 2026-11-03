package com.holysweet.sweetbusiness.discovery.entity;

import com.holysweet.sweetbusiness.util.Constants;
import com.holysweet.sweetbusiness.discovery.simulation.EntityLootSimulator;
import com.holysweet.sweetbusiness.discovery.entity.EntityDiscoverer.EntityProperties;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

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

    public static DiscoveryResult discoverLoot(MinecraftServer server, Map<EntityType<?>, EntityProperties> entities) {
        ServerLevel level = server.overworld();
        Map<EntityType<?>, Map<Item, Double>> expectedDrops = new HashMap<>();
        Map<EntityType<?>, String> failures = new HashMap<>();
        EntityLootSimulator simulator = new EntityLootSimulator(level);

        for (var entry : entities.entrySet()) {
            EntityType<?> type = entry.getKey();
            EntityProperties properties = entry.getValue();
            if (!properties.hasLootTable()) continue;

            try {
                if (!(type.create(level) instanceof Mob mob)) {
                    failures.put(type, "Could not construct mob for loot simulation");
                    continue;
                }
                expectedDrops.put(type, simulator.simulate(
                        mob, properties.lootTable(), Constants.MOB_LOOT_SIMULATION_ITERATIONS));
            } catch (RuntimeException | LinkageError exception) {
                failures.put(type, exception.getClass().getName() + ": " + exception.getMessage());
            }
        }
        return new DiscoveryResult(expectedDrops, failures);
    }
}
