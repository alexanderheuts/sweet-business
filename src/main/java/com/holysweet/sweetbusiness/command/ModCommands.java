package com.holysweet.sweetbusiness.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        // Build the root /sb command
        dispatcher.register(Commands.literal("sb")
                .then(ValueCommand.register(buildContext))
                .then(TraceCommand.register(buildContext))
                .then(RecipeTypesCommand.register())
                .then(LedgerCommand.register())
                .then(ContractCommand.register())
        );
    }
}
