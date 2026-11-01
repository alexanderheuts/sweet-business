package com.holysweet.sweetbusiness.contract;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record DeliveryLine(ResourceLocation itemId, int requiredQuantity, int deliveredQuantity, double unitValue) {

    public DeliveryLine {
        Objects.requireNonNull(itemId);
        if (requiredQuantity <= 0) {
            throw new IllegalArgumentException("Required quantity must be positive");
        }
        if (deliveredQuantity < 0 || deliveredQuantity > requiredQuantity) {
            throw new IllegalArgumentException("Delivered quantity must be between zero and the required quantity");
        }
        if (!Double.isFinite(unitValue) || unitValue <= 0) {
            throw new IllegalArgumentException("Unit value must be finite and positive");
        }
    }

    public DeliveryLine(ResourceLocation itemId, int requiredQuantity, double unitValue) {
        this(itemId, requiredQuantity, 0, unitValue);
    }

    public int remainingQuantity() {
        return requiredQuantity - deliveredQuantity;
    }

    public boolean isComplete() {
        return deliveredQuantity == requiredQuantity;
    }

    public double value() {
        return requiredQuantity * unitValue;
    }
}
