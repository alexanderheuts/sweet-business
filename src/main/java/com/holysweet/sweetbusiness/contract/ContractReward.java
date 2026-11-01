package com.holysweet.sweetbusiness.contract;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ContractReward(ResourceLocation type) {

    public static final ContractReward STUB = new ContractReward(ResourceLocation.fromNamespaceAndPath("sweetbusiness", "stub"));

    public ContractReward {
        Objects.requireNonNull(type);
    }
}
