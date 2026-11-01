package com.holysweet.sweetbusiness.command;

import com.holysweet.sweetbusiness.valuation.ValuationEngine;
import com.holysweet.sweetbusiness.valuation.ItemValuation;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;

public class ValueCommand {

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("value")
                .then(Commands.argument("item", ItemArgument.item(buildContext))
                        .executes(ValueCommand::execute)
                );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        // Extract the item from the player's argument
        ItemInput itemInput = ItemArgument.getItem(context, "item");
        Item item = itemInput.getItem();
        String itemName = BuiltInRegistries.ITEM.getKey(item).toString();

        // Fetch the valuation from the engine
        ItemValuation valuation = ValuationEngine.getInstance().getValuation(item);

        if (valuation == ItemValuation.UNKNOWN) {
            context.getSource().sendFailure(Component.literal("§cNo valuation data found for: " + itemName));
            return 0;
        }

        // Determine the item's origin based on depth and override flags
        String output = evaluateAndBuildValue(valuation, itemName);

        context.getSource().sendSuccess(() -> Component.literal(output), false);

        return Command.SINGLE_SUCCESS;
    }

    private static @NotNull String evaluateAndBuildValue(ItemValuation valuation, String itemName) {
        String origin;
        if (valuation.isConfigOverride()) {
            origin = "§6[Config Override]";
        } else if (valuation.depth() == 0) {
            origin = "§a[Base Resource]";
        } else {
            origin = "§b[Crafted - Depth " + valuation.depth() + "]";
        }

        // Format the output message
        return String.format("§fValue of §e%s§f: §a%.5f §7(Math: %.5f) §r%s",
                itemName,
                valuation.value(),
                valuation.calculatedValue(),
                origin);
    }
}