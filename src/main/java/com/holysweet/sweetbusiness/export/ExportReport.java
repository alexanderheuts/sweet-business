package com.holysweet.sweetbusiness.export;

import net.minecraft.server.MinecraftServer;

import java.util.Optional;

public interface ExportReport {

    String name();

    // Return cached data in report order, or empty when unavailable; do not trigger recalculation.
    Optional<ExportData> snapshot(MinecraftServer server);

    static void validateName(String name) {
        if (!name.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid export report name: " + name);
    }
}
