package com.holysweet.sweetbusiness.export.report;

import com.holysweet.sweetbusiness.export.ExportData;
import com.holysweet.sweetbusiness.export.ExportReport;
import com.holysweet.sweetbusiness.valuation.ValuationEngine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class AnchorReport implements ExportReport {

    @Override
    public String name() {
        return "anchors";
    }

    @Override
    public Optional<ExportData> snapshot(MinecraftServer server) {
        return ValuationEngine.getInstance().getAnchors().map(anchors -> {
            List<List<?>> rows = new ArrayList<>();
            anchors.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> BuiltInRegistries.ITEM.getKey(entry.getKey()).toString()))
                    .forEach(entry -> {
                        var anchor = entry.getValue();
                        var value = anchor.valuation();
                        rows.add(List.of(BuiltInRegistries.ITEM.getKey(entry.getKey()).toString(), value.value(),
                                value.calculatedValue(), value.techLevel(), value.isBulkFarmable(),
                                anchor.source(), anchor.sourceDetail()));
                    });
            return new ExportData(List.of("item_id", "value", "calculated_value", "tier", "bulk_farmable",
                    "source", "source_detail"), rows);
        });
    }
}
