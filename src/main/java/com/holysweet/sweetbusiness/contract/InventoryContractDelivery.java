package com.holysweet.sweetbusiness.contract;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InventoryContractDelivery {

    private InventoryContractDelivery() {}

    public static ContractManager.DeliveryResult deliver(Contract contract, Inventory inventory, ItemStack contractStack) {
        Map<ResourceLocation, ItemStack> requestedItems = new HashMap<>();
        for (DeliveryLine line : contract.lines()) {
            if (line.isComplete()) continue;
            BuiltInRegistries.ITEM.getOptional(line.itemId()).ifPresent(item ->
                    requestedItems.put(line.itemId(), item.getDefaultInstance()));
        }

        Map<Integer, ResourceLocation> matchingSlots = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> offeredItems = new HashMap<>();
        // Inventory.items contains hotbar and main storage; skip the hotbar prefix.
        for (int slot = Inventory.getSelectionSize(); slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (stack.isEmpty() || stack == contractStack) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            ItemStack requested = requestedItems.get(itemId);
            if (requested != null && ItemStack.isSameItemSameComponents(stack, requested)) {
                matchingSlots.put(slot, itemId);
                offeredItems.merge(itemId, stack.getCount(), Integer::sum);
            }
        }

        var result = ContractManager.deliver(contract, offeredItems);
        Map<ResourceLocation, Integer> remaining = new HashMap<>(result.acceptedItems());
        matchingSlots.forEach((slot, itemId) -> {
            ItemStack stack = inventory.items.get(slot);
            int taken = Math.min(stack.getCount(), remaining.getOrDefault(itemId, 0));
            stack.shrink(taken);
            remaining.computeIfPresent(itemId, (item, quantity) -> quantity - taken);
        });
        if (!result.acceptedItems().isEmpty()) inventory.setChanged();
        return result;
    }
}
