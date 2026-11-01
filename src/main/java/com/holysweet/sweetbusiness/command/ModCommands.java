package com.holysweet.sweetbusiness.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        // Build the root /sb command
        dispatcher.register(Commands.literal("sb")
                // .requires(source -> source.hasPermission(2)) // Uncomment if you want OP-only
                .then(ValueCommand.register(buildContext))
        );
    }
}