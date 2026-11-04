package com.holysweet.sweetbusiness.contract;

import com.holysweet.sweetbusiness.item.ContractItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ContractRedemption {

    private ContractRedemption() {}

    public static boolean redeem(Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer)) return false;
        Contract contract = ContractItem.getContract(stack).orElse(null);
        if (contract == null || !contract.isComplete() || !contract.reward().equals(ContractReward.DIAMOND)) {
            return false;
        }

        // Consumption also applies in creative mode: a contract can only pay out once.
        stack.shrink(1);
        ItemStack reward = new ItemStack(Items.DIAMOND);
        player.getInventory().add(reward);
        if (!reward.isEmpty()) {
            var entity = player.drop(reward, false);
            if (entity != null) {
                entity.setNoPickUpDelay();
                entity.setTarget(player.getUUID());
            }
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return true;
    }
}
