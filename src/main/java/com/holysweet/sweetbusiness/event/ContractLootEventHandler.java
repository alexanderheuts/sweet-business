package com.holysweet.sweetbusiness.event;

import com.holysweet.sweetbusiness.config.ServerConfig;
import com.holysweet.sweetbusiness.contract.ContractGenerationService;
import com.holysweet.sweetbusiness.item.ContractItem;
import com.holysweet.sweetbusiness.valuation.ValuationEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ContractLootEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContractLootEventHandler.class);

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        var mob = event.getEntity();
        if (!(mob instanceof Enemy) || !(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        var mobTier = ValuationEngine.getInstance().getEntityTiers().get(mob.getType());
        if (mobTier == null) return;

        double chance = ServerConfig.CONTRACT_DROP_CHANCE.get();
        if (chance <= 0 || mob.getRandom().nextDouble() >= chance / 100.0) return;

        try {
            ContractGenerationService.generate(player, mobTier.tier()).ifPresent(contract ->
                    event.getDrops().add(new ItemEntity(mob.level(), mob.getX(), mob.getY(), mob.getZ(),
                            ContractItem.create(contract))));
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Could not generate mob-drop contract for {}", player.getUUID(), exception);
        }
    }
}
