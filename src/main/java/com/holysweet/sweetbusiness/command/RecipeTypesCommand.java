package com.holysweet.sweetbusiness.command;

import com.holysweet.sweetbusiness.valuation.ValuationEngine;
import com.holysweet.sweetbusiness.valuation.discovery.RecipeTypeDiscoverer.RecipeTypeProperties;
import com.holysweet.sweetbusiness.valuation.discovery.RecipeTypeDiscoverer.TierSource;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

public class RecipeTypesCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("recipeTypes")
                .executes(context -> showRecipeTypes(context, entry -> true))
                .then(Commands.literal("source")
                        .then(sourceFilter("global", TierSource.GLOBAL_DEFAULT))
                        .then(sourceFilter("modid", TierSource.NAMESPACE))
                        .then(sourceFilter("specific", TierSource.OVERRIDE)))
                .then(Commands.literal("mod")
                        .then(Commands.argument("modid", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        getRecipeTypes(context.getSource()).keySet().stream()
                                                .map(ResourceLocation::getNamespace).distinct().sorted(), builder))
                                .executes(context -> {
                                    String modid = StringArgumentType.getString(context, "modid");
                                    return showRecipeTypes(context, entry -> entry.getKey().getNamespace().equals(modid));
                                })))
                .then(Commands.literal("type")
                        .then(Commands.argument("recipeType", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        getRecipeTypes(context.getSource()).keySet(), builder))
                                .executes(context -> {
                                    ResourceLocation typeId = ResourceLocationArgument.getId(context, "recipeType");
                                    return showRecipeTypes(context, entry -> entry.getKey().equals(typeId));
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sourceFilter(String name, TierSource source) {
        return Commands.literal(name)
                .executes(context -> showRecipeTypes(context, entry -> entry.getValue().source() == source));
    }

    private static Map<ResourceLocation, RecipeTypeProperties> getRecipeTypes(CommandSourceStack source) {
        var registry = source.getServer().registryAccess().registryOrThrow(Registries.RECIPE_TYPE);
        Map<ResourceLocation, RecipeTypeProperties> recipeTypes = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        ValuationEngine.getInstance().getProcessingRules().forEach((type, properties) -> {
            ResourceLocation id = registry.getKey(type);
            if (id != null) {
                recipeTypes.put(id, properties);
            }
        });
        return recipeTypes;
    }

    private static int showRecipeTypes(CommandContext<CommandSourceStack> context,
                                      Predicate<Map.Entry<ResourceLocation, RecipeTypeProperties>> filter) {
        var matches = getRecipeTypes(context.getSource()).entrySet().stream().filter(filter).toList();
        if (matches.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No matching recipe types found in the economy's processing rules."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Recipe types: " + matches.size()), false);
        for (var entry : matches) {
            RecipeTypeProperties properties = entry.getValue();
            String message = String.format("%s | Tier: %d | Processing: %s | Source: %s | Rule: %s",
                    entry.getKey(), properties.techTier(), properties.isManual() ? "manual" : "industrial",
                    sourceName(properties.source()), properties.matchedRule());
            context.getSource().sendSuccess(() -> Component.literal(message), false);
        }
        return matches.size();
    }

    private static String sourceName(TierSource source) {
        return switch (source) {
            case GLOBAL_DEFAULT -> "global";
            case NAMESPACE -> "modid";
            case OVERRIDE -> "specific";
        };
    }
}
