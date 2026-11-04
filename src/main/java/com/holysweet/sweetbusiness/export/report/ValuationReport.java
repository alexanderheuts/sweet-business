package com.holysweet.sweetbusiness.export.report;

import com.holysweet.sweetbusiness.export.ExportData;
import com.holysweet.sweetbusiness.export.ExportReport;
import com.holysweet.sweetbusiness.valuation.ItemValuation;
import com.holysweet.sweetbusiness.valuation.ValuationEngine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public final class ValuationReport implements ExportReport {

    @Override
    public String name() {
        return "valuations";
    }

    @Override
    public Optional<ExportData> snapshot(MinecraftServer server) {
        return ValuationEngine.getInstance().getEconomy().map(economy -> {
            var baseline = economy.baseline();
            var manufacturing = economy.manufacturing();
            var items = new HashSet<>(baseline.itemValues().keySet());
            items.addAll(manufacturing.itemValues().keySet());
            List<List<?>> rows = new ArrayList<>();
            items.stream().sorted(Comparator.comparing(BuiltInRegistries.ITEM::getKey)).forEach(item -> {
                ItemValuation value = baseline.getValuation(item);
                ItemValuation cost = manufacturing.getValuation(item);
                var step = baseline.sourceSteps().get(item);
                rows.add(List.of(BuiltInRegistries.ITEM.getKey(item).toString(),
                        value.isKnown() ? value.value() : "", value.isKnown() ? value.calculatedCost() : "",
                        value.isKnown() ? value.depth() : "", value.isKnown() ? value.techLevel() : "",
                        value.isKnown() ? value.isBulkFarmable() : "", value.isConfigOverride(),
                        step == null ? "" : step.source(), step == null ? "" : step.detail(),
                        cost.isKnown() ? cost.value() : "", cost.isKnown() ? cost.calculatedCost() : "",
                        cost.isKnown() ? cost.depth() : ""));
            });
            return new ExportData(List.of("item_id", "baseline_value", "baseline_calculated_cost", "baseline_depth",
                    "tier", "bulk_farmable", "config_override", "source", "source_detail",
                    "manufacturing_cost", "manufacturing_calculated_cost", "manufacturing_depth"), rows);
        });
    }
}
