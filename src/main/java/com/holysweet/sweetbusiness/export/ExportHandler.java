package com.holysweet.sweetbusiness.export;

import com.holysweet.sweetbusiness.SweetBusiness;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

public final class ExportHandler {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);

    private ExportHandler() {}

    public record ExportResult(Path path, int rowCount) {}

    public static Optional<ExportResult> export(ExportReport report, MinecraftServer server) throws IOException {
        String name = report.name();
        ExportReport.validateName(name);

        Optional<ExportData> snapshot = report.snapshot(server);
        if (snapshot.isEmpty()) return Optional.empty();

        Path directory = FMLPaths.CONFIGDIR.get().resolve(SweetBusiness.MODID).resolve("export").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        return Optional.of(writeExport(directory, name, snapshot.get()));
    }

    private static ExportResult writeExport(Path directory, String name, ExportData data) throws IOException {
        String prefix = name + "-" + LocalDateTime.now().format(TIMESTAMP);
        for (int suffix = 0; ; suffix++) {
            String filename = prefix + (suffix == 0 ? "" : "-" + suffix) + ".csv";
            Path path = directory.resolve(filename);
            BufferedWriter writer;
            try {
                writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException exception) {
                continue;
            }

            try (writer) {
                CsvWriter.write(writer, data);
            } catch (IOException | RuntimeException exception) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
                throw exception;
            }
            return new ExportResult(path, data.rows().size());
        }
    }
}
