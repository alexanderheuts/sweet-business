package com.holysweet.sweetbusiness.event;

import com.holysweet.sweetbusiness.progression.LedgerSavedData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.UUID;

public class LedgerEventHandler {

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Post event) {
        recordItem(event.getPlayer(), event.getOriginalStack());
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        recordItem(event.getEntity(), event.getCrafting());
    }

    @SubscribeEvent
    public void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        recordItem(event.getEntity(), event.getSmelting());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        recordInventory(event.getEntity());
    }

    @SubscribeEvent
    public void onContainerClosed(PlayerContainerEvent.Close event) {
        recordInventory(event.getEntity());
    }

    private static void recordItem(Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer) || stack.isEmpty()) return;

        LedgerSavedData.get(serverPlayer.server)
                .recordItem(resolveOwnerId(serverPlayer), BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static void recordInventory(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        for (int slot = 0; slot < serverPlayer.getInventory().getContainerSize(); slot++) {
            recordItem(serverPlayer, serverPlayer.getInventory().getItem(slot));
        }
    }

    private static UUID resolveOwnerId(ServerPlayer player) {
        return player.getUUID();
    }
}
