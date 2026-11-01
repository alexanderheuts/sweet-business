package com.holysweet.sweetbusiness.command;

import com.holysweet.sweetbusiness.progression.LedgerSavedData;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class LedgerCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("ledger")
                .requires(source -> source.hasPermission(2))
                .executes(LedgerCommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayerOrException();
        var ledger = LedgerSavedData.get(source.getServer()).getLedger(player.getUUID());

        String message = String.format("Ledger | Discovered items: %d | Dirty: %s",
                ledger.getDiscoveredItemCount(), ledger.isDirty());
        source.sendSuccess(() -> Component.literal(message), false);
        return Command.SINGLE_SUCCESS;
    }
}
