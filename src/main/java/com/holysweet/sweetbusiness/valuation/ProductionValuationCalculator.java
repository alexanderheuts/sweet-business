package com.holysweet.sweetbusiness.valuation;

import com.holysweet.sweetbusiness.evaluation.renewable.CropEvaluator.CropEvaluation;
import com.holysweet.sweetbusiness.util.Constants;
import com.holysweet.sweetbusiness.valuation.EconomyInputs.RecipeInput;
import com.holysweet.sweetbusiness.valuation.EconomyInputs.IngredientInput;
import com.holysweet.sweetbusiness.valuation.EconomyResult.AnchorSource;
import com.holysweet.sweetbusiness.valuation.EconomyResult.SourceStep;
import com.holysweet.sweetbusiness.valuation.EconomyResult.IngredientUse;
import com.holysweet.sweetbusiness.valuation.renewable.CropValuator;
import com.holysweet.sweetbusiness.valuation.renewable.TreeValuator;
import com.holysweet.sweetbusiness.valuation.renewable.TreeValuator.TreeValue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Shares anchor preparation and production math between the two route-selection policies.
 */
final class ProductionValuationCalculator {

    private final EconomyInputs inputs;
    private final boolean shortestPath;
    private final Map<Item, ItemValuation> itemValues = new HashMap<>();
    private Map<Item, ItemValuation> ingredientValues = itemValues;
    private final Map<Item, AnchorSource> anchorSources = new HashMap<>();
    private final Map<Item, SourceStep> sourceSteps = new HashMap<>();
    private final Map<Item, ItemValuation> diagnosticCosts = new HashMap<>();
    private Set<Item> fixedAnchors = Set.of();
    private int baselineDepth = Integer.MAX_VALUE;

    private ProductionValuationCalculator(EconomyInputs inputs, boolean shortestPath) {
        this.inputs = inputs;
        this.shortestPath = shortestPath;
    }

    static EconomyResult calculate(EconomyInputs inputs, boolean includeConfiguredAnchors, boolean shortestPath) {
        return new ProductionValuationCalculator(inputs, shortestPath).calculate(includeConfiguredAnchors);
    }

    private EconomyResult calculate(boolean includeConfiguredAnchors) {
        if (includeConfiguredAnchors) {
            inputs.configuredAnchors().forEach((item, anchor) -> {
                itemValues.put(item, anchor.value());
                anchorSources.put(item, anchor.source());
            });
        }
        Set<Item> worldgenAnchors = seedBlockAnchors();
        mergeRenewableValues(inputs.naturalCropAnchors(), worldgenAnchors);
        recordNaturalCropSources(inputs.crops());
        worldgenAnchors.addAll(inputs.naturalCropAnchors().keySet());
        mergeTreeValues(inputs.naturalTreeAnchors(), worldgenAnchors);
        inputs.naturalTreeAnchors().forEach((item, anchor) -> anchorSources.putIfAbsent(item,
                new AnchorSource("tree", BuiltInRegistries.ITEM.getKey(anchor.plantingItem()).toString())));
        worldgenAnchors.addAll(inputs.naturalTreeAnchors().keySet());
        seedMobAnchors(worldgenAnchors);
        if (shortestPath) {
            fixedAnchors = Set.copyOf(itemValues.keySet());
            itemValues.forEach((item, value) -> {
                if (value.isConfigOverride() && value.calculatedCost() > 0) {
                    diagnosticCosts.put(item, new ItemValuation(value.calculatedCost(), 0, value.techLevel(), value.isBulkFarmable()));
                }
            });
        }
        anchorSources.forEach((item, source) -> sourceSteps.put(item,
                new SourceStep(source.type(), source.detail(), 1, 0, List.of())));
        Set<Item> renewableAnchors = new HashSet<>(inputs.naturalCropAnchors().keySet());
        renewableAnchors.addAll(inputs.naturalTreeAnchors().keySet());
        int iterations = runManufacturingCostLoop(inputs.crops(), renewableAnchors, worldgenAnchors);
        int unresolved = (int) inputs.recipes().stream()
                .filter(recipe -> !itemValues.getOrDefault(recipe.output(), ItemValuation.UNKNOWN).isKnown()).count();
        return new EconomyResult(itemValues, anchorSources, sourceSteps, iterations, unresolved);
    }

    private Set<Item> seedBlockAnchors() {
        Set<Item> worldgenAnchors = new HashSet<>();
        for (var block : inputs.blocks()) {
            Item blockItem = block.item();
            if (blockItem != Items.AIR) {
                worldgenAnchors.add(blockItem);
                ItemValuation existing = itemValues.get(blockItem);
                if (existing == null) {
                    itemValues.put(blockItem, new ItemValuation(block.value(), 0, block.techLevel(), false));
                    anchorSources.put(blockItem, block.source());
                } else if (existing.isConfigOverride()) {
                    itemValues.put(blockItem, existing.withCalculatedCost(block.value()));
                }
            }
            for (var drop : block.drops().entrySet()) {
                Item item = drop.getKey();
                float value = drop.getValue().floatValue();
                worldgenAnchors.add(item);
                ItemValuation existing = itemValues.get(item);
                if (existing == null) {
                    itemValues.put(item, new ItemValuation(value, 0, block.techLevel(), false));
                    anchorSources.put(item, new AnchorSource("block_drop", block.lootTableId().toString()));
                } else if (existing.isConfigOverride()) {
                    itemValues.put(item, existing.withCalculatedCost(value));
                }
            }
        }
        return worldgenAnchors;
    }

    private void seedMobAnchors(Set<Item> worldgenAnchors) {
        var anchors = inputs.mobAnchors();
        anchors.forEach((item, anchor) -> {
            ItemValuation candidate = anchor.valuation();
            ItemValuation existing = itemValues.get(item);
            if (existing == null) {
                itemValues.put(item, candidate);
                anchorSources.put(item, new AnchorSource("mob_loot", anchor.lootTableId().toString()));
                return;
            }

            boolean bulkFarmable = existing.isBulkFarmable() || candidate.isBulkFarmable();
            ItemValuation merged = existing;
            if (!worldgenAnchors.contains(item)) {
                if (existing.isConfigOverride()) {
                    merged = existing.withCalculatedCost(candidate.value());
                } else if (candidate.value() < existing.value()
                        || (candidate.value() == existing.value() && candidate.techLevel() < existing.techLevel())) {
                    merged = candidate;
                    anchorSources.put(item, new AnchorSource("mob_loot", anchor.lootTableId().toString()));
                }
            }
            itemValues.put(item, merged.withBulkFarmable(bulkFarmable));
        });
    }

    private boolean mergeRenewableValues(Map<Item, ItemValuation> candidates, Set<Item> anchors) {
        boolean changed = false;
        for (var entry : candidates.entrySet()) {
            Item item = entry.getKey();
            ItemValuation candidate = entry.getValue();
            ItemValuation existing = itemValues.get(item);
            ItemValuation merged = existing;
            if (existing == null) {
                merged = candidate;
                anchorSources.remove(item);
            } else if (existing.isConfigOverride()) {
                if (!candidate.isConfigOverride() && (existing.calculatedCost() <= 0
                        || candidate.value() < existing.calculatedCost() - 0.00001f)) {
                    merged = existing.withCalculatedCost(candidate.value());
                }
            } else if (!anchors.contains(item) && (candidate.value() < existing.value() - 0.00001f
                    || (candidate.value() == existing.value() && candidate.techLevel() < existing.techLevel()))) {
                merged = candidate;
                anchorSources.remove(item);
            }
            if (merged != existing) sourceSteps.remove(item);
            merged = merged.withBulkFarmable(true);
            if (!merged.equals(existing)) {
                itemValues.put(item, merged);
                changed = true;
            }
        }
        return changed;
    }

    private boolean mergeTreeValues(Map<Item, TreeValue> candidates, Set<Item> anchors) {
        Map<Item, ItemValuation> values = new HashMap<>();
        candidates.forEach((item, candidate) -> values.put(item, candidate.valuation()));
        return mergeRenewableValues(values, anchors);
    }

    private void recordNaturalCropSources(Map<Item, CropEvaluation> crops) {
        crops.entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> BuiltInRegistries.ITEM.getKey(entry.getKey()).toString()))
                .forEach(entry -> {
                    if (!entry.getValue().hasWorldgenSource()) return;
                    AnchorSource source = new AnchorSource("crop", BuiltInRegistries.ITEM.getKey(entry.getKey()).toString());
                    anchorSources.putIfAbsent(entry.getKey(), source);
                    entry.getValue().harvestItems().forEach(item -> anchorSources.putIfAbsent(item, source));
                });
    }

    private int runManufacturingCostLoop(Map<Item, CropEvaluation> crops, Set<Item> renewableAnchors, Set<Item> worldgenAnchors) {
        if (shortestPath) return runBaselineLoop(renewableAnchors);
        Set<Item> bulkRenewableItems = new HashSet<>(crops.keySet());
        crops.values().forEach(crop -> bulkRenewableItems.addAll(crop.harvestItems()));
        inputs.trees().values().forEach(tree -> bulkRenewableItems.addAll(tree.outputs().keySet()));
        Set<ResourceLocation> seedRecipes = new HashSet<>();
        crops.values().forEach(crop -> seedRecipes.addAll(crop.seedRecipes()));
        inputs.recipes().stream().filter(this::isTreeSeedRecipe).forEach(recipe -> seedRecipes.add(recipe.id()));
        boolean changed = true;
        int iteration = 0;
        while (changed && iteration < Constants.ENGINE_MAX_ITERATIONS) {
            changed = mergeRenewableValues(CropValuator.valueHarvests(crops, itemValues), worldgenAnchors);
            changed |= mergeTreeValues(TreeValuator.valueHarvests(inputs.trees(), inputs.treeSettings(), itemValues), worldgenAnchors);
            iteration++;

            for (RecipeInput recipe : inputs.recipes()) {
                // Seed multiplication must not repeatedly discount an already valued growing cycle.
                if (seedRecipes.contains(recipe.id())) {
                    Item result = recipe.output();
                    ItemValuation existing = itemValues.get(result);
                    if (existing != null && existing.value() > 0) continue;
                }
                if (updateRecipeManufacturingCost(recipe, renewableAnchors, bulkRenewableItems)) {
                    changed = true;
                }
            }
        }
        return iteration;
    }

    private int runBaselineLoop(Set<Item> renewableAnchors) {
        List<RecipeInput> recipes = inputs.recipes().stream()
                .sorted(Comparator.comparing(recipe -> recipe.id().toString())).toList();
        int maximumDepth = recipes.size() + inputs.crops().size() + inputs.trees().size() + 1;
        // Finalize one depth layer at a time. A shallow route can cost more than an
        // earlier-discovered deep route; downstream prices must never retain that old discount.
        for (baselineDepth = 1; baselineDepth <= maximumDepth; baselineDepth++) {
            ingredientValues = Map.copyOf(itemValues);
            boolean changed = updateBaselineHarvests();
            for (RecipeInput recipe : recipes) {
                changed |= updateRecipeManufacturingCost(recipe, renewableAnchors, Set.of());
            }
            if (!changed) return baselineDepth;
        }
        return maximumDepth;
    }

    private boolean isTreeSeedRecipe(RecipeInput recipe) {
        var tree = inputs.trees().get(recipe.output());
        if (tree == null || recipe.ingredients().isEmpty()) return false;
        return recipe.ingredients().stream().allMatch(ingredient -> ingredient.alternatives().stream()
                .anyMatch(option -> option.item() != recipe.output() && tree.outputs().containsKey(option.item())));
    }

    private boolean updateRecipeManufacturingCost(RecipeInput recipe, Set<Item> renewableAnchors, Set<Item> bulkRenewableItems) {
        Item resultItem = recipe.output();
        ItemValuation current = itemValues.get(resultItem);
        if (shortestPath && fixedAnchors.contains(resultItem) && !current.isConfigOverride()) return false;
        if (shortestPath && current != null && !current.isConfigOverride() && current.depth() < baselineDepth) return false;
        if (renewableAnchors.contains(resultItem) && current != null && !current.isConfigOverride()) return false;
        RecipeType<?> type = recipe.type();

        // 1. Resolve processing properties
        var typeProps = inputs.processing().properties(type);

        float totalValue = 0.0f;
        int maxIngredientDepth = 0;
        int recipeTechLevel = typeProps.techTier(); // Start at the machine's tier
        boolean isRecipeBulkFarmable = !typeProps.isManual(); // If the machine is manual, it is NOT bulk farmable

        boolean canResolve = true;
        List<IngredientUse> selectedIngredients = new ArrayList<>();
        // Minimize output depth first, then choose the cheapest alternatives within that budget.
        int ingredientDepthLimit = shortestPath ? recipe.ingredients().stream()
                .mapToInt(ingredient -> ingredient.alternatives().stream()
                        .map(option -> ingredientValues.get(option.item()))
                        .filter(value -> value != null && value.isKnown() && Float.isFinite(value.value())
                                && value.value() > 0.00001f && value.depth() < baselineDepth)
                        .mapToInt(ItemValuation::depth).min().orElse(Integer.MAX_VALUE))
                .max().orElse(0) : Integer.MAX_VALUE;

        for (IngredientInput ingredient : recipe.ingredients()) {
            ItemValuation cheapest = findCheapestIngredientCost(ingredient, selectedIngredients, ingredientDepthLimit);

            // Short-circuit abort on the first unpriced/missing ingredient
            if (cheapest == null || cheapest.value() <= 0.00001f) {
                canResolve = false;
                break;
            }

            totalValue += cheapest.value();
            recipeTechLevel = Math.max(recipeTechLevel, cheapest.techLevel());
            maxIngredientDepth = Math.max(maxIngredientDepth, cheapest.depth());

            if (!cheapest.isBulkFarmable()) {
                isRecipeBulkFarmable = false; // Taint the entire product as non-bulk if any ingredient was manual
            }
        }

        // If it can't resolve OR totalValue is still 0, the recipe had no valid ingredient path.
        if (!canResolve || totalValue <= 0.00001f) {
            return false;
        }

        if (!shortestPath && bulkRenewableItems.contains(resultItem)) isRecipeBulkFarmable = true;
        float finalValue = (float) inputs.processing().tax(type).calculateUnitValue(totalValue, recipe.outputCount());
        if (shortestPath && (!Float.isFinite(finalValue) || finalValue <= 0)) return false;
        int newDepth = maxIngredientDepth + 1;

        ItemValuation existingValuation = itemValues.get(resultItem);
        boolean shouldUpdateActive = false;
        boolean shouldUpdateShadow = false;

        // Float math epsilon to prevent infinite depth flip-flopping in loops
        float epsilon = 0.00001f;

        ItemValuation candidate = new ItemValuation(finalValue, newDepth, recipeTechLevel, isRecipeBulkFarmable);
        if (shortestPath) {
            if (existingValuation != null && existingValuation.isConfigOverride()) {
                ItemValuation diagnostic = diagnosticCosts.get(resultItem);
                if (diagnostic == null || prefersBaseline(candidate, diagnostic)) {
                    diagnosticCosts.put(resultItem, candidate);
                    itemValues.put(resultItem, existingValuation.withCalculatedCost(finalValue));
                    return true;
                }
                return false;
            }
            SourceStep step = new SourceStep("recipe", recipe.id().toString(), recipe.outputCount(),
                    inputs.processing().tax(type).totalPercentage(), selectedIngredients);
            if (existingValuation == null || prefersBaseline(candidate, existingValuation)
                    || sameBaselineRank(candidate, existingValuation) && prefersStep(step, sourceSteps.get(resultItem))) {
                itemValues.put(resultItem, candidate);
                anchorSources.remove(resultItem);
                sourceSteps.put(resultItem, step);
                return true;
            }
            return false;
        }

        if (existingValuation == null) {
            shouldUpdateActive = true;
        } else {
            if (existingValuation.isConfigOverride()) {
                // Update shadow math if it's uninitialized, or we found a strictly cheaper recipe path
                if (existingValuation.calculatedCost() == 0.0f || finalValue < existingValuation.calculatedCost() - epsilon) {
                    shouldUpdateShadow = true;
                }
            } else if (finalValue < existingValuation.value() - epsilon) {
                shouldUpdateActive = true;
            } else if (!existingValuation.isBulkFarmable() && isRecipeBulkFarmable) {
                // Override: The cost might be identical or slightly higher, but this new path is fully automated
                shouldUpdateActive = true;
            }
        }

        if (shouldUpdateShadow) {
            itemValues.put(resultItem, existingValuation.withCalculatedCost(finalValue));
            return true; // Return true so subsequent recipes using the shadow value also update
        } else if (shouldUpdateActive) {
            itemValues.put(resultItem, new ItemValuation(finalValue, newDepth, recipeTechLevel, isRecipeBulkFarmable));
            anchorSources.remove(resultItem);
            sourceSteps.put(resultItem, new SourceStep("recipe", recipe.id().toString(), recipe.outputCount(),
                    inputs.processing().tax(type).totalPercentage(), selectedIngredients));
            return true;
        }

        return false;
    }

    private ItemValuation findCheapestIngredientCost(IngredientInput ingredient, List<IngredientUse> selectedIngredients,
                                                    int depthLimit) {
        float cheapestValue = Float.MAX_VALUE;
        int bestDepth = 0;
        int bestTechLevel = 0;
        boolean bestIsBulkFarmable = false; // Assume worst case initially
        boolean resolved = false;
        Item selectedItem = null;
        Item selectedRemainder = null;
        var alternatives = shortestPath ? ingredient.alternatives().stream()
                .sorted(Comparator.comparing(option -> BuiltInRegistries.ITEM.getKey(option.item()).toString())).toList()
                : ingredient.alternatives();

        for (var option : alternatives) {
            Item item = option.item();
            ItemValuation valuation = ingredientValues.get(item);

            if (valuation != null && (!shortestPath || valuation.isKnown()
                    && Float.isFinite(valuation.value()) && valuation.value() > 0.00001f
                    && valuation.depth() <= depthLimit && valuation.depth() < baselineDepth)) {
                float currentCost = valuation.value();

                // Remainders were captured from the ingredient stacks when the inputs were prepared.
                if (option.remainder() != null) {
                    Item remainderItem = option.remainder();
                    ItemValuation remainderValuation = ingredientValues.get(remainderItem);

                    if (remainderValuation != null) {
                        currentCost = Math.max(0.01f, currentCost - remainderValuation.value());
                    }
                }

                boolean shouldUpdate = false;

                if (!resolved) {
                    shouldUpdate = true;
                } else if (shortestPath) {
                    shouldUpdate = currentCost < cheapestValue;
                } else if (currentCost < cheapestValue) {
                    shouldUpdate = true;
                } else if (!bestIsBulkFarmable && valuation.isBulkFarmable()) {
                    // Override: The cost might be slightly higher, but this variant is fully automated
                    shouldUpdate = true;
                }

                if (shouldUpdate) {
                    cheapestValue = currentCost;
                    bestDepth = valuation.depth();
                    bestTechLevel = valuation.techLevel();
                    bestIsBulkFarmable = valuation.isBulkFarmable();
                    resolved = true;
                    selectedItem = item;
                    selectedRemainder = option.remainder();
                }
            }
        }

        if (resolved) selectedIngredients.add(new IngredientUse(selectedItem, selectedRemainder, cheapestValue));
        return resolved ? new ItemValuation(cheapestValue, bestDepth, bestTechLevel, bestIsBulkFarmable) : null;
    }

    private static boolean prefersBaseline(ItemValuation candidate, ItemValuation existing) {
        return candidate.depth() < existing.depth()
                || candidate.depth() == existing.depth() && candidate.value() < existing.value();
    }

    private static boolean sameBaselineRank(ItemValuation first, ItemValuation second) {
        return first.depth() == second.depth() && first.value() == second.value();
    }

    private static boolean prefersStep(SourceStep candidate, SourceStep existing) {
        if (existing == null) return true;
        int order = (candidate.source() + ":" + candidate.detail()).compareTo(existing.source() + ":" + existing.detail());
        return order < 0 || order == 0 && !candidate.equals(existing);
    }

    private boolean updateBaselineHarvests() {
        boolean changed = false;
        for (Item seed : inputs.crops().keySet().stream().sorted(Comparator.comparing(BuiltInRegistries.ITEM::getKey)).toList()) {
            var candidates = CropValuator.valueHarvests(Map.of(seed, inputs.crops().get(seed)), ingredientValues);
            changed |= mergeBaselineHarvests(seed, "crop", candidates);
        }
        for (Item seed : inputs.trees().keySet().stream().sorted(Comparator.comparing(BuiltInRegistries.ITEM::getKey)).toList()) {
            var candidates = TreeValuator.valueHarvests(Map.of(seed, inputs.trees().get(seed)), inputs.treeSettings(), ingredientValues);
            Map<Item, ItemValuation> values = new HashMap<>();
            candidates.forEach((item, value) -> values.put(item, value.valuation()));
            changed |= mergeBaselineHarvests(seed, "tree", values);
        }
        return changed;
    }

    private boolean mergeBaselineHarvests(Item seed, String source, Map<Item, ItemValuation> candidates) {
        boolean changed = false;
        for (var entry : candidates.entrySet()) {
            Item item = entry.getKey();
            if (item == seed || fixedAnchors.contains(item)) continue;
            ItemValuation candidate = entry.getValue();
            ItemValuation existing = itemValues.get(item);
            if (candidate.depth() > baselineDepth || existing != null && existing.depth() < baselineDepth) continue;
            SourceStep step = new SourceStep(source, BuiltInRegistries.ITEM.getKey(seed).toString(), 1, 0,
                    List.of(new IngredientUse(seed, null, itemValues.get(seed).value())));
            if (existing == null || prefersBaseline(candidate, existing)
                    || sameBaselineRank(candidate, existing) && prefersStep(step, sourceSteps.get(item))) {
                itemValues.put(item, candidate);
                anchorSources.remove(item);
                sourceSteps.put(item, step);
                changed = true;
            }
        }
        return changed;
    }

}
