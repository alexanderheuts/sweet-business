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
        boolean isTargetUnknown = (valuation == ItemValuation.UNKNOWN);

        printHeader(context, itemName, valuation);

        if (!isTargetUnknown && valuation.depth() == 0) {
            printOrigin(context, valuation);
        } else {
            traceRecipes(context, targetItem, valuation);
        }

        send(context, "§8§m====================================");

        return Command.SINGLE_SUCCESS;
    }

    private static void traceRecipes(CommandContext<CommandSourceStack> context, Item targetItem, ItemValuation targetValuation) {
        var registryAccess = context.getSource().getServer().registryAccess();
        Iterable<RecipeHolder<?>> allRecipes = context.getSource().getServer().getRecipeManager().getRecipes();

        boolean foundRecipe = false;

        for (RecipeHolder<?> holder : allRecipes) {
            ItemStack resultStack = holder.value().getResultItem(registryAccess);
            if (resultStack.isEmpty() || resultStack.getItem() != targetItem) continue;

            foundRecipe = true;
            evaluateAndPrintRecipe(context, holder, resultStack.getCount(), targetValuation);
        }

        if (!foundRecipe) {
            send(context, "§cNo standard recipes found that produce this item.");
        }
    }

    private static void evaluateAndPrintRecipe(CommandContext<CommandSourceStack> context, RecipeHolder<?> holder, int yield, ItemValuation targetValuation) {
        Map<String, Integer> ingredientCounts = new LinkedHashMap<>();
        double totalCost = calculateCostAndGroupIngredients(holder.value().getIngredients(), ingredientCounts);

        // Handle empty ingredient recipes (like Smithing)
        if (ingredientCounts.isEmpty()) {
            send(context, "§8- §f" + holder.id().getPath() + " §7(Yield " + yield + ")");
            send(context, "    §7Manufacturing cost: §cUNRESOLVABLE (No standard ingredients)");
            return;
        }

        boolean isUnresolvable = (totalCost < 0); // -1.0 means missing ingredients
        var processingTax = ValuationEngine.getInstance().getProcessingTax(holder.value().getType());
        double unitCost = (isUnresolvable || yield <= 0) ? 0 : processingTax.calculateUnitValue(totalCost, yield);
        boolean isWinner = !isUnresolvable && targetValuation != ItemValuation.UNKNOWN && Math.abs(unitCost - targetValuation.value()) < 0.0001;

        printRecipeDetails(context, holder.id().getPath(), yield, unitCost, isWinner, ingredientCounts, isUnresolvable);
        send(context, String.format("    §7Processing markup: %.2f%% (Base: %.2f%%, Tier: %.2f%%, Manual: %.2f%%)",
                processingTax.totalPercentage(), processingTax.basePercentage(),
                processingTax.tierPercentage(), processingTax.manualPercentage()));
    }

    private static double calculateCostAndGroupIngredients(NonNullList<Ingredient> ingredients, Map<String, Integer> ingredientCounts) {
        double totalCost = 0.0;
        boolean hasUnknown = false;

        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;

            Item ingItem = ingredient.getItems()[0].getItem();
            String shortName = BuiltInRegistries.ITEM.getKey(ingItem).getPath();
            ItemValuation ingVal = ValuationEngine.getInstance().getValuation(ingItem);

            if (ingVal == ItemValuation.UNKNOWN) {
                shortName += " §c(Unpriced)§7"; // Flag the missing ingredient in the formula
                hasUnknown = true;
            } else {
                totalCost += ingVal.value();
            }

            ingredientCounts.put(shortName, ingredientCounts.getOrDefault(shortName, 0) + 1);
        }

        return hasUnknown ? -1.0 : totalCost; // Return -1.0 as a sentinel if unresolvable
    }

    private static void printRecipeDetails(CommandContext<CommandSourceStack> context, String recipeId, int yield, double unitCost, boolean isWinner, Map<String, Integer> counts, boolean isUnresolvable) {
        String prefix = isWinner ? "§a§l> §r" : "§8- §r";

        String costString;
        if (isUnresolvable) {
            costString = "§cUNKNOWN (Missing ingredient prices)";
        } else if (isWinner) {
            costString = "§a" + String.format("%.5f", unitCost) + " §a[MATCHES ACTIVE VALUE]";
        } else {
            costString = "§e" + String.format("%.5f", unitCost);
        }

        StringBuilder formula = new StringBuilder();
        counts.forEach((name, count) -> {
            if (!formula.isEmpty()) formula.append(" §8+ ");
            formula.append("§f").append(count).append("x §7").append(name);
        });

        send(context, prefix + "§f" + recipeId + " §7(Yield " + yield + ")");
        send(context, "    §7Manufacturing cost: " + costString);
        send(context, "    §7From: " + formula);
    }

    private static void printHeader(CommandContext<CommandSourceStack> context, String itemName, ItemValuation valuation) {
        send(context, "§8§m==========[§r §6" + itemName.toUpperCase() + " §8§m]==========");
        if (valuation == ItemValuation.UNKNOWN) {
            send(context, "§7Active value: §cUNKNOWN  §7|  Selected route depth: §c-");
        } else {
            send(context, "§7Active value: §a" + String.format("%.5f", valuation.value()) + "  §7|  Selected route depth: §b" + valuation.depth());
            send(context, "§7Calculated cost: §a" + String.format("%.5f", valuation.calculatedCost()));
        }
    }

    private static void printOrigin(CommandContext<CommandSourceStack> context, ItemValuation valuation) {
        String origin = valuation.isConfigOverride() ? "§eConfig Override" : "§aBase Resource / WorldGen";
        send(context, "§7Origin: " + origin);
    }

    private static void send(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
    }
}
