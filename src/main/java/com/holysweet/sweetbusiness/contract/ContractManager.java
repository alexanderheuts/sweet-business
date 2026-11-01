package com.holysweet.sweetbusiness.contract;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ContractManager {

    private ContractManager() {}

    public record DeliveryResult(Contract contract, Map<ResourceLocation, Integer> acceptedItems) {
        public DeliveryResult {
            Objects.requireNonNull(contract);
            acceptedItems = Map.copyOf(acceptedItems);
        }

        public int acceptedQuantity(ResourceLocation itemId) {
            return acceptedItems.getOrDefault(itemId, 0);
        }
    }

    public static DeliveryResult deliver(Contract contract, ResourceLocation itemId, int offeredQuantity) {
        return deliver(contract, Map.of(itemId, offeredQuantity));
    }

    public static DeliveryResult deliver(Contract contract, Map<ResourceLocation, Integer> offeredItems) {
        Objects.requireNonNull(contract);
        validateOfferedItems(offeredItems);

        List<DeliveryLine> updatedLines = new ArrayList<>();
        Map<ResourceLocation, Integer> acceptedItems = new LinkedHashMap<>();
        for (DeliveryLine line : contract.lines()) {
            int accepted = Math.min(offeredItems.getOrDefault(line.itemId(), 0), line.remainingQuantity());
            if (accepted == 0) {
                updatedLines.add(line);
                continue;
            }

            acceptedItems.put(line.itemId(), accepted);
            updatedLines.add(new DeliveryLine(line.itemId(), line.requiredQuantity(),
                    line.deliveredQuantity() + accepted, line.unitValue()));
        }

        Contract updatedContract = acceptedItems.isEmpty() ? contract
                : new Contract(contract.id(), contract.tier(), updatedLines, contract.reward());
        return new DeliveryResult(updatedContract, acceptedItems);
    }

    private static void validateOfferedItems(Map<ResourceLocation, Integer> offeredItems) {
        offeredItems.forEach((itemId, quantity) -> {
            Objects.requireNonNull(itemId);
            Objects.requireNonNull(quantity);
            if (quantity < 0) {
                throw new IllegalArgumentException("Offered quantity must be non-negative");
            }
        });
    }
}
