package com.holysweet.sweetbusiness.command;

import com.holysweet.sweetbusiness.util.Constants;
import com.holysweet.sweetbusiness.valuation.EntityTierEvaluator.EntityTier;
import com.holysweet.sweetbusiness.valuation.ValuationEngine;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.Comparator;
import java.util.Map;
import java.util.Locale;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EntitiesCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("entities")
                .requires(source -> source.hasPermission(2))
                .executes(context -> showEntities(context, entry -> true))
                .then(Commands.literal("unknown").executes(EntitiesCommand::showUnknownEntities))
                .then(Commands.literal("tier")
                        .then(Commands.argument("tier", IntegerArgumentType.integer(Constants.BASE_MOB_TIER, Constants.MAX_MOB_TIER))
                                .executes(context -> {
                                    int tier = IntegerArgumentType.getInteger(context, "tier");
                                    return showEntities(context, entry -> entry.getValue().tier() == tier);
                                })))
                .then(Commands.literal("type")
                        .then(Commands.argument("entity", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        Stream.concat(getEntityTiers().keySet().stream(), getUnknownEntities().keySet().stream())
                                                .distinct().sorted(Comparator.comparing(ResourceLocation::toString)), builder))
                                .executes(context -> {
                                    ResourceLocation id = ResourceLocationArgument.getId(context, "entity");
                                    String failure = getUnknownEntities().get(id);
                                    if (failure != null) {
                                        sendUnknown(context.getSource(), id, failure);
                                        return 1;
                                    }
                                    return showEntities(context, entry -> entry.getKey().equals(id));
                                })));
    }

    private static Map<ResourceLocation, EntityTier> getEntityTiers() {
        return byEntityId(ValuationEngine.getInstance().getEntityTiers());
    }

    private static Map<ResourceLocation, String> getUnknownEntities() {
        return byEntityId(ValuationEngine.getInstance().getUnidentifiedEntities());
    }

    private static <T> Map<ResourceLocation, T> byEntityId(Map<EntityType<?>, T> values) {
        Map<ResourceLocation, T> sorted = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        values.forEach((type, value) -> sorted.put(BuiltInRegistries.ENTITY_TYPE.getKey(type), value));
        return sorted;
    }

    private static int showUnknownEntities(CommandContext<CommandSourceStack> context) {
        var unknown = getUnknownEntities();
        var source = context.getSource();
        source.sendSuccess(() -> Component.literal("Unidentified entities: " + unknown.size()), false);
        unknown.forEach((id, reason) -> sendUnknown(source, id, reason));
        return unknown.size();
    }

    private static void sendUnknown(CommandSourceStack source, ResourceLocation id, String reason) {
        source.sendSuccess(() -> Component.literal(id + " | Tier: unknown | Reason: " + reason), false);
    }

    private static int showEntities(CommandContext<CommandSourceStack> context,
                                    Predicate<Map.Entry<ResourceLocation, EntityTier>> filter) {
        var matches = getEntityTiers().entrySet().stream().filter(filter).toList();
        var source = context.getSource();
        if (matches.isEmpty()) {
            source.sendFailure(Component.literal("No matching entities with calculated tier data found."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Entities: " + matches.size()), false);
        for (var entry : matches) {
            EntityTier tier = entry.getValue();
            String promotions = tier.promotions().isEmpty() ? "none (baseline)"
                    : tier.promotions().stream().sorted().map(Enum::name).collect(Collectors.joining(", "));
            String message = String.format(Locale.ROOT, "%s | Tier: %d | Median XP: %.2f | Promotions: %s",
                    entry.getKey(), tier.tier(), tier.medianXp(), promotions);
            source.sendSuccess(() -> Component.literal(message), false);
        }
        return matches.size();
    }
}
