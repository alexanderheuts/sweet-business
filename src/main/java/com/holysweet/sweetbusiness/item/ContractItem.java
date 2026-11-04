package com.holysweet.sweetbusiness.item;

import com.holysweet.sweetbusiness.contract.Contract;
import com.holysweet.sweetbusiness.contract.ContractReward;
import com.holysweet.sweetbusiness.contract.DeliveryLine;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@ParametersAreNonnullByDefault
public final class ContractItem extends Item {

    public ContractItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Contract contract) {
        ItemStack stack = new ItemStack(ModItems.CONTRACT.get());
        stack.set(ModDataComponents.CONTRACT.get(), Objects.requireNonNull(contract));
        return stack;
    }

    public static Optional<Contract> getContract(ItemStack stack) {
        return stack.is(ModItems.CONTRACT.get())
                ? Optional.ofNullable(stack.get(ModDataComponents.CONTRACT.get())) : Optional.empty();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Contract contract = getContract(stack).orElse(null);
        if (contract == null) {
            tooltip.add(Component.translatable("tooltip.sweetbusiness.contract.missing").withStyle(ChatFormatting.RED));
            return;
        }

        tooltip.add(Component.translatable("tooltip.sweetbusiness.contract.tier", contract.tier())
                .withStyle(ChatFormatting.GRAY));
        for (DeliveryLine line : contract.lines()) {
            tooltip.add(Component.translatable("tooltip.sweetbusiness.contract.line", itemName(line.itemId()),
                            line.deliveredQuantity(), line.requiredQuantity())
                    .withStyle(line.isComplete() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        MutableComponent reward = contract.reward().equals(ContractReward.DIAMOND)
                ? Component.translatable("tooltip.sweetbusiness.contract.diamond_reward", Items.DIAMOND.getDescription())
                : Component.translatable("tooltip.sweetbusiness.contract.reward", contract.reward().type().toString());
        tooltip.add(reward.withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(contract.isComplete()
                        ? "tooltip.sweetbusiness.contract.complete" : "tooltip.sweetbusiness.contract.incomplete")
                .withStyle(contract.isComplete() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        if (flag.isAdvanced()) {
            tooltip.add(Component.literal(contract.id().toString()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static Component itemName(ResourceLocation itemId) {
        return BuiltInRegistries.ITEM.containsKey(itemId)
                ? BuiltInRegistries.ITEM.get(itemId).getDescription() : Component.literal(itemId.toString());
    }
}
