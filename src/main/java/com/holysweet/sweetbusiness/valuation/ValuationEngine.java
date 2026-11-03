package com.holysweet.sweetbusiness.valuation;

import com.holysweet.sweetbusiness.discovery.recipe.RecipeTypeDiscoverer.RecipeTypeProperties;
import com.holysweet.sweetbusiness.evaluation.entity.EntityTierEvaluator.EntityTier;
import com.holysweet.sweetbusiness.valuation.EconomyResult.AnchorEntry;
import com.holysweet.sweetbusiness.valuation.recipe.ProcessingTax;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.Map;
import java.util.Optional;

public final class ValuationEngine {

    private static final ValuationEngine INSTANCE = new ValuationEngine();

    private volatile Economy economy;

    private record Economy(EconomyInputs inputs, EconomyResult result) {}

    private ValuationEngine() {}

    public static ValuationEngine getInstance() {
        return INSTANCE;
    }

    public ItemValuation getValuation(Item item) {
        Economy current = economy;
        return current == null ? ItemValuation.UNKNOWN : current.result().getValuation(item);
    }

    public Optional<Map<Item, ItemValuation>> getValuations() {
        return Optional.ofNullable(economy).map(current -> current.result().itemValues());
    }

    public Optional<Map<Item, AnchorEntry>> getAnchors() {
        return Optional.ofNullable(economy).map(current -> current.result().getAnchors());
    }

    public Optional<EconomyInputs> getCalculationInputs() {
        return Optional.ofNullable(economy).map(Economy::inputs);
    }

    public Map<EntityType<?>, EntityTier> getEntityTiers() {
        Economy current = economy;
        return current == null ? Map.of() : current.inputs().entityTiers();
    }

    public Map<EntityType<?>, String> getUnidentifiedEntities() {
        Economy current = economy;
        return current == null ? Map.of() : current.inputs().unidentifiedEntities();
    }

    public Map<EntityType<?>, Map<Item, Double>> getEntityDrops() {
        Economy current = economy;
        return current == null ? Map.of() : current.inputs().entityDrops();
    }

    public Map<RecipeType<?>, RecipeTypeProperties> getProcessingRules() {
        Economy current = economy;
        return current == null ? Map.of() : current.inputs().processing().rules();
    }

    public ProcessingTax getProcessingTax(RecipeType<?> type) {
        Economy current = economy;
        return current == null ? EconomyBuilder.captureProcessingSettings(Map.of()).tax(type)
                : current.inputs().processing().tax(type);
    }

    public void calculateEconomy(MinecraftServer server) {
        EconomyInputs inputs = EconomyBuilder.prepare(server);
        EconomyResult result = ValuationCalculator.calculate(inputs, true);
        EconomyBuilder.logResult(inputs, result);
        // Publish the complete inputs and result together; failed or isolated calculations cannot alter live values.
        economy = new Economy(inputs, result);
    }

    public void clear() {
        economy = null;
    }
}
