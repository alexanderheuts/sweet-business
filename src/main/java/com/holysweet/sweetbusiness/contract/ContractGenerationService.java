package com.holysweet.sweetbusiness.contract;

import com.holysweet.sweetbusiness.config.ServerConfig;
import com.holysweet.sweetbusiness.contract.generator.ContractGenerator;
import com.holysweet.sweetbusiness.progression.CraftableSetManager;
import com.holysweet.sweetbusiness.valuation.ItemValuation;
import com.holysweet.sweetbusiness.valuation.ValuationEngine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ContractGenerationService {

    private ContractGenerationService() {}

    public static Optional<Contract> generate(ServerPlayer player, int tier) {
        ContractRules.validateTier(tier);
        var itemIds = CraftableSetManager.getInstance().getCraftableItems(player.server, player.getUUID());
        if (itemIds.isEmpty()) return Optional.empty();

        Map<ResourceLocation, ItemValuation> valuations = new HashMap<>();
        for (ResourceLocation itemId : itemIds) {
            BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(item ->
                    valuations.put(itemId, ValuationEngine.getInstance().getValuation(item)));
        }
        return ContractGenerator.generate(UUID.randomUUID(), tier, valuations,
                ServerConfig.CONTRACT_EXTRA_LINE_CHANCE.get(), ThreadLocalRandom.current().nextLong());
    }
}
