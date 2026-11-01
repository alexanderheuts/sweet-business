package com.holysweet.sweetbusiness.contract;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record Contract(UUID id, int tier, List<DeliveryLine> lines, ContractReward reward) {

    public Contract {
        Objects.requireNonNull(id);
        Objects.requireNonNull(reward);
        if (tier < 1 || tier > 5) {
            throw new IllegalArgumentException("Contract tier must be between 1 and 5");
        }

        lines = mergeLines(lines);
        if (lines.isEmpty() || lines.size() > tier * 2) {
            throw new IllegalArgumentException("Contract must have between 1 and " + tier * 2 + " delivery lines");
        }
    }

    public Contract(UUID id, int tier, List<DeliveryLine> lines) {
        this(id, tier, lines, ContractReward.STUB);
    }

    public double totalValue() {
        return lines.stream().mapToDouble(DeliveryLine::value).sum();
    }

    public double rewardValue() {
        return totalValue();
    }

    public boolean isComplete() {
        return lines.stream().allMatch(DeliveryLine::isComplete);
    }

    private static List<DeliveryLine> mergeLines(List<DeliveryLine> lines) {
        Map<ResourceLocation, DeliveryLine> merged = new LinkedHashMap<>();
        for (DeliveryLine line : lines) {
            merged.merge(line.itemId(), line, (existing, duplicate) -> new DeliveryLine(existing.itemId(),
                    Math.addExact(existing.requiredQuantity(), duplicate.requiredQuantity()),
                    Math.addExact(existing.deliveredQuantity(), duplicate.deliveredQuantity()), existing.unitValue()));
        }
        return List.copyOf(merged.values());
    }
}
