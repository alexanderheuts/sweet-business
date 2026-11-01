package com.holysweet.sweetbusiness.command;

import com.holysweet.sweetbusiness.valuation.ItemValuation;
import com.holysweet.sweetbusiness.valuation.ValuationEngine;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.LinkedHashMap;
import java.util.Map;

public class TraceCommand {

    public static ArgumentBuilder<CommandSourceStack, ?> register(CommandBuildContext buildContext) {
        return Commands.literal("trace")
                .then(Commands.argument("item", ItemArgument.item(buildContext))
                        .executes(TraceCommand::execute)
                );
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        Item targetItem = ItemArgument.getItem(context, "item").getItem();
        String itemName = BuiltInRegistries.ITEM.getKey(targetItem).getPath();

        ItemValuation valuation = ValuationEngine.getInstance().getValuation(targetItem);

        if (valuation == ItemValuation.UNKNOWN) {
            send(context, "§cNo valuation data found for: " + itemName);
            return 0;
        }

        printHeader(context, itemName, valuation);

        if (valuation.depth() == 0) {
            printOrigin(context, valuation);
            return Command.SINGLE_SUCCESS;
        }

        traceRecipes(context, targetItem, valuation.value());

        send(context, "§8§m====================================");
        return Command.SINGLE_SUCCESS;
    }

    private static void traceRecipes(CommandContext<CommandSourceStack> context, Item targetItem, double targetValue) {
        var registryAccess = context.getSource().getServer().registryAccess();
        Iterable<RecipeHolder<?>> allRecipes = context.getSource().getServer().getRecipeManager().getRecipes();

        boolean foundRecipe = false;

        for (RecipeHolder<?> holder : allRecipes) {
            ItemStack resultStack = holder.value().getResultItem(registryAccess);
            if (resultStack.isEmpty() || resultStack.getItem() != targetItem) continue;

            foundRecipe = true;
            evaluateAndPrintRecipe(context, holder, resultStack.getCount(), targetValue);
        }

        if (!foundRecipe) {
            send(context, "§cWarning: Depth is > 0 but no recipes produce this item!");
        }
    }

    private static void evaluateAndPrintRecipe(CommandContext<CommandSourceStack> context, RecipeHolder<?> holder, int yield, double targetValue) {
        Map<String, Integer> ingredientCounts = new LinkedHashMap<>();
        double totalCost = calculateCostAndGroupIngredients(holder.value().getIngredients(), ingredientCounts);

        double unitCost = yield > 0 ? totalCost / yield : 0;
        boolean isWinner = Math.abs(unitCost - targetValue) < 0.0001;

        printRecipeDetails(context, holder.id().getPath(), yield, unitCost, isWinner, ingredientCounts);
    }

    private static double calculateCostAndGroupIngredients(NonNullList<Ingredient> ingredients, Map<String, Integer> ingredientCounts) {
        double totalCost = 0.0;

        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;

            Item ingItem = ingredient.getItems()[0].getItem();
            String shortName = BuiltInRegistries.ITEM.getKey(ingItem).getPath();
            ItemValuation ingVal = ValuationEngine.getInstance().getValuation(ingItem);

            totalCost += (ingVal != ItemValuation.UNKNOWN) ? ingVal.value() : 0.0;
            ingredientCounts.put(shortName, ingredientCounts.getOrDefault(shortName, 0) + 1);
        }

        return totalCost;
    }

    private static void printRecipeDetails(CommandContext<CommandSourceStack> context, String recipeId, int yield, double unitCost, boolean isWinner, Map<String, Integer> counts) {
        String prefix = isWinner ? "§a§l> §r" : "§8- §r";
        String costColor = isWinner ? "§a" : "§e";
        String winnerTag = isWinner ? " §a[WINNER]" : "";

        StringBuilder formula = new StringBuilder();
        counts.forEach((name, count) -> {
            if (!formula.isEmpty()) formula.append(" §8+ ");
            formula.append("§f").append(count).append("x §7").append(name);
        });

        send(context, prefix + "§f" + recipeId + " §7(Yield " + yield + ")");
        send(context, "    §7Cost: " + costColor + String.format("%.5f", unitCost) + winnerTag);
        send(context, "    §7From: " + formula);
    }

    private static void printHeader(CommandContext<CommandSourceStack> context, String itemName, ItemValuation valuation) {
        send(context, "§8§m==========[§r §6" + itemName.toUpperCase() + " §8§m]==========");
        send(context, "§7Final Value: §a" + String.format("%.5f", valuation.value()) + "  §7|  Depth: §b" + valuation.depth());
    }

    private static void printOrigin(CommandContext<CommandSourceStack> context, ItemValuation valuation) {
        String origin = valuation.isConfigOverride() ? "§eConfig Override" : "§aBase Resource / WorldGen";
        send(context, "§7Origin: " + origin);
    }

    private static void send(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
    }
}