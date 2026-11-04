package com.holysweet.sweetbusiness.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

public final class ContractCodecs {

    private ContractCodecs() {}

    private static final Codec<DeliveryLine> DELIVERY_LINE = ExtraCodecs.catchDecoderException(
            RecordCodecBuilder.create(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("item").forGetter(DeliveryLine::itemId),
                    Codec.INT.fieldOf("required").forGetter(DeliveryLine::requiredQuantity),
                    Codec.INT.fieldOf("delivered").forGetter(DeliveryLine::deliveredQuantity),
                    Codec.DOUBLE.fieldOf("unit_value").forGetter(DeliveryLine::unitValue)
            ).apply(instance, DeliveryLine::new)));

    private static final Codec<ContractReward> REWARD = ResourceLocation.CODEC
            .xmap(ContractReward::new, ContractReward::type);

    // Keep constructor validation active when reading saved or network-provided data.
    public static final Codec<Contract> CONTRACT = ExtraCodecs.catchDecoderException(
            RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("id").forGetter(Contract::id),
                    Codec.INT.fieldOf("tier").forGetter(Contract::tier),
                    DELIVERY_LINE.listOf().fieldOf("lines").forGetter(Contract::lines),
                    REWARD.fieldOf("reward").forGetter(Contract::reward)
            ).apply(instance, Contract::new)));
}
