package com.holysweet.sweetbusiness.contract;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ContractReward(ResourceLocation type) {

    public static final ContractReward DIAMOND = new ContractReward(ResourceLocation.fromNamespaceAndPath("sweetbusiness", "diamond"));

    public ContractReward {
        Objects.requireNonNull(type);
    }
}
