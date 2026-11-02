package com.holysweet.sweetbusiness.command;

import com.holysweet.sweetbusiness.config.Config;
import com.holysweet.sweetbusiness.contract.Contract;
import com.holysweet.sweetbusiness.contract.DeliveryLine;
import com.holysweet.sweetbusiness.contract.generator.ContractGenerator;
import com.holysweet.sweetbusiness.progression.CraftableSetManager;
import com.holysweet.sweetbusiness.util.Constants;
import com.holysweet.sweetbusiness.valuation.ItemValuation;
import com.holysweet.sweetbusiness.valuation.ValuationEngine;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ContractCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContractCommand.class);

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("contract")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("mock")
                        .then(Commands.argument("tier", IntegerArgumentType.integer(
                                        Constants.MIN_CONTRACT_TIER, Constants.MAX_CONTRACT_TIER))
                                .executes(ContractCommand::generateMock)));
    }

    private static int generateMock(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayerOrException();
        int tier = IntegerArgumentType.getInteger(context, "tier");

        try {
            var contract = ContractGenerator.generate(UUID.randomUUID(), tier, getCraftableValuations(player),
                    Config.CONTRACT_EXTRA_LINE_CHANCE.get(), ThreadLocalRandom.current().nextLong());
            if (contract.isEmpty()) {
                source.sendFailure(Component.literal("No mock contract generated: no eligible items could fill its lines."));
                return 0;
            }

            printContract(source, contract.get());
            return Command.SINGLE_SUCCESS;
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Could not generate tier {} mock contract for {}", tier, player.getUUID(), exception);
            source.sendFailure(Component.literal("Could not generate mock contract: " + exception.getMessage()));
            return 0;
        }
    }

    private static Map<ResourceLocation, ItemValuation> getCraftableValuations(ServerPlayer player) {
        var itemIds = CraftableSetManager.getInstance().getCraftableItems(player.server, player.getUUID());
        Map<ResourceLocation, ItemValuation> valuations = new HashMap<>();
        for (ResourceLocation itemId : itemIds) {
            BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(item ->
                    valuations.put(itemId, ValuationEngine.getInstance().getValuation(item)));
        }
        return valuations;
    }

    private static void printContract(CommandSourceStack source, Contract contract) {
        send(source, "Mock contract | Tier: %d | Lines: %d", contract.tier(), contract.lines().size());
        send(source, "ID: %s", contract.id());
        for (int index = 0; index < contract.lines().size(); index++) {
            DeliveryLine line = contract.lines().get(index);
            send(source, "%d. %s | Delivered: %d/%d | Unit value: %.5f | Line value: %.5f",
                    index + 1, line.itemId(), line.deliveredQuantity(), line.requiredQuantity(), line.unitValue(), line.value());
        }
        send(source, "Total delivery value: %.5f | Complete: %s", contract.totalValue(), contract.isComplete());
        send(source, "Reward: %s | Reward value: %.5f", contract.reward().type(), contract.rewardValue());
    }

    private static void send(CommandSourceStack source, String format, Object... arguments) {
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, format, arguments)), false);
    }
}
