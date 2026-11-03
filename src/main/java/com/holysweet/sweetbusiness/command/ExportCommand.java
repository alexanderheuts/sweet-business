package com.holysweet.sweetbusiness.command;

import com.holysweet.sweetbusiness.export.ExportHandler;
import com.holysweet.sweetbusiness.export.ExportReport;
import com.holysweet.sweetbusiness.export.report.AnchorReport;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ExportCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ExportCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return register(List.of(new AnchorReport()));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register(List<ExportReport> reports) {
        List<ExportReport> ordered = reports.stream().sorted(Comparator.comparing(ExportReport::name)).toList();
        var command = Commands.literal("export")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    String message = ordered.isEmpty() ? "No export reports are registered."
                            : "Available reports: " + String.join(", ", ordered.stream().map(ExportReport::name).toList());
                    context.getSource().sendSuccess(() -> Component.literal(message), false);
                    return Command.SINGLE_SUCCESS;
                });
        Set<String> names = new HashSet<>();
        for (ExportReport report : ordered) {
            ExportReport.validateName(report.name());
            if (!names.add(report.name())) {
                throw new IllegalArgumentException("Duplicate export report name: " + report.name());
            }
            command.then(Commands.literal(report.name()).executes(context -> execute(context.getSource(), report)));
        }
        return command;
    }

    private static int execute(CommandSourceStack source, ExportReport report) {
        try {
            var result = ExportHandler.export(report, source.getServer());
            if (result.isEmpty()) {
                source.sendFailure(Component.literal("Export data unavailable for " + report.name() + "."));
                return 0;
            }
            String path = result.get().path().toString();
            Component link = Component.literal(path).withStyle(style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, path))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to copy export path"))));
            source.sendSuccess(() -> Component.literal("Exported " + result.get().rowCount() + " rows to ")
                    .append(link), false);
            return Command.SINGLE_SUCCESS;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Could not export report {}", report.name(), exception);
            source.sendFailure(Component.literal("Could not export " + report.name() + "; see server log for details."));
            return 0;
        }
    }
}
