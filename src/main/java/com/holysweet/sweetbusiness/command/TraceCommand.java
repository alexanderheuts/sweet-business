package com.holysweet.sweetbusiness.command;

import com.holysweet.sweetbusiness.valuation.EconomyResult;
import com.holysweet.sweetbusiness.valuation.ValuationEngine;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TraceCommand {

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("trace")
                .then(Commands.argument("item", ItemArgument.item(buildContext))
                        .executes(context -> execute(context, false))
                        .then(Commands.literal("manufacturing").executes(context -> execute(context, true))));
    }

    private static int execute(CommandContext<CommandSourceStack> context, boolean manufacturing) {
        var snapshot = ValuationEngine.getInstance().getEconomy();
        if (snapshot.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Economy data is unavailable."));
            return 0;
        }
        Item item = ItemArgument.getItem(context, "item").getItem();
        EconomyResult result = manufacturing ? snapshot.get().manufacturing() : snapshot.get().baseline();
        send(context, manufacturing ? "Manufacturing cost trace" : "Shortest-path baseline trace");
        printStep(context, result, item, "", new HashSet<>());
        return Command.SINGLE_SUCCESS;
    }

    private static void printStep(CommandContext<CommandSourceStack> context, EconomyResult result,
                                  Item item, String indent, Set<Item> expanded) {
        var value = result.getValuation(item);
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        if (!value.isKnown()) {
            send(context, indent + itemId + " | UNKNOWN");
            return;
        }
        send(context, String.format(Locale.ROOT, "%s%s | Value: %.5f | Depth: %d | Tier: %d",
                indent, itemId, value.value(), value.depth(), value.techLevel()));
        if (!expanded.add(item)) {
            send(context, indent + "  (already shown)");
            return;
        }
        var step = result.sourceSteps().get(item);
        if (step == null) {
            send(context, indent + "  Source step unavailable.");
            return;
        }
        send(context, indent + "  Source: " + step.source() + " | " + step.detail());
        if (value.isConfigOverride()) {
            send(context, String.format(Locale.ROOT, "%s  Hard config anchor | Diagnostic cost: %.5f",
                    indent, value.calculatedCost()));
        }
        if (step.ingredients().isEmpty()) return;
        send(context, String.format(Locale.ROOT, "%s  Yield: %d | Processing markup: %.2f%%",
                indent, step.outputCount(), step.markupPercentage()));
        Map<Item, Integer> counts = new LinkedHashMap<>();
        step.ingredients().forEach(ingredient -> counts.merge(ingredient.item(), 1, Integer::sum));
        double inputCost = step.ingredients().stream().mapToDouble(EconomyResult.IngredientUse::cost).sum();
        send(context, String.format(Locale.ROOT, "%s  Input cost after remainder credits: %.5f", indent, inputCost));
        step.ingredients().stream().filter(ingredient -> ingredient.remainder() != null).forEach(ingredient ->
                send(context, indent + "  Returned item: " + BuiltInRegistries.ITEM.getKey(ingredient.remainder())));
        counts.forEach((ingredient, count) -> {
            send(context, indent + "  " + count + "x ingredient:");
            printStep(context, result, ingredient, indent + "    ", expanded);
        });
    }

    private static void send(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
    }
}
