package com.holysweet.sweetbusiness.export.report;

import com.holysweet.sweetbusiness.export.ExportData;
import com.holysweet.sweetbusiness.export.ExportReport;
import com.holysweet.sweetbusiness.recipe.RecipeIndex;
import com.holysweet.sweetbusiness.valuation.ValuationCalculator;
import com.holysweet.sweetbusiness.valuation.ValuationEngine;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

public final class BaremetalUnresolvedReport implements ExportReport {

    @Override
    public String name() {
        return "unresolved-baremetal";
    }

    @Override
    public Optional<ExportData> snapshot(MinecraftServer server) {
        return ValuationEngine.getInstance().getCalculationInputs().map(inputs -> {
            var result = ValuationCalculator.calculate(inputs, false);
            return UnresolvedReport.buildReport(inputs.recipes(), result.itemValues(), RecipeIndex.getInstance());
        });
    }
}
