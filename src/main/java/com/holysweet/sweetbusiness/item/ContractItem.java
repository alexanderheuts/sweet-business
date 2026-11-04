package com.holysweet.sweetbusiness.item;

import com.holysweet.sweetbusiness.contract.Contract;
import com.holysweet.sweetbusiness.contract.ContractReward;
import com.holysweet.sweetbusiness.contract.ContractRedemption;
import com.holysweet.sweetbusiness.contract.DeliveryLine;
import com.holysweet.sweetbusiness.contract.InventoryContractDelivery;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
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
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        Contract contract = getContract(stack).orElse(null);
        if (contract == null || !player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (contract.isComplete()) {
            if (!level.isClientSide) {
                boolean redeemed = ContractRedemption.redeem(player, stack);
                player.displayClientMessage(Component.translatable(redeemed
                        ? "message.sweetbusiness.contract.redeemed"
                        : "message.sweetbusiness.contract.unsupported_reward"), true);
            }
            // The reward may now occupy the consumed contract's hand slot.
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(usedHand), level.isClientSide);
        }
        if (!level.isClientSide) {
            var result = InventoryContractDelivery.deliver(contract, player.getInventory(), stack);
            int delivered = result.acceptedItems().values().stream().mapToInt(Integer::intValue).sum();
            if (delivered > 0) {
                stack.set(ModDataComponents.CONTRACT.get(), result.contract());
                player.containerMenu.broadcastChanges();
            }
            Component message = result.contract().isComplete()
                    ? Component.translatable("message.sweetbusiness.contract.complete")
                    : delivered > 0 ? Component.translatable("message.sweetbusiness.contract.delivered", delivered)
                    : Component.translatable("message.sweetbusiness.contract.no_matching_items");
            player.displayClientMessage(message, true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
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
