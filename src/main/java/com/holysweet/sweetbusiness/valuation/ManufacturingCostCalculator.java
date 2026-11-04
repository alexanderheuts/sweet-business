package com.holysweet.sweetbusiness.valuation;

import com.holysweet.sweetbusiness.evaluation.renewable.CropEvaluator.CropEvaluation;
import com.holysweet.sweetbusiness.util.Constants;
import com.holysweet.sweetbusiness.valuation.EconomyInputs.RecipeInput;
import com.holysweet.sweetbusiness.valuation.EconomyInputs.IngredientInput;
import com.holysweet.sweetbusiness.valuation.EconomyResult.AnchorSource;
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

/**
 * Calculates manufacturing costs from prepared anchors and production relationships.
 * Preserves the existing bulk-farmable route preference as well as price comparisons.
 */
public final class ManufacturingCostCalculator {

    private final EconomyInputs inputs;
    private final Map<Item, ItemValuation> itemValues = new HashMap<>();
    private final Map<Item, AnchorSource> anchorSources = new HashMap<>();

    private ManufacturingCostCalculator(EconomyInputs inputs) {
        this.inputs = inputs;
    }

    public static EconomyResult calculate(EconomyInputs inputs, boolean includeConfiguredAnchors) {
        return new ManufacturingCostCalculator(inputs).calculate(includeConfiguredAnchors);
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
        Set<Item> renewableAnchors = new HashSet<>(inputs.naturalCropAnchors().keySet());
        renewableAnchors.addAll(inputs.naturalTreeAnchors().keySet());
        int iterations = runManufacturingCostLoop(inputs.crops(), renewableAnchors, worldgenAnchors);
        int unresolved = (int) inputs.recipes().stream()
                .filter(recipe -> !itemValues.getOrDefault(recipe.output(), ItemValuation.UNKNOWN).isKnown()).count();
        return new EconomyResult(itemValues, anchorSources, iterations, unresolved);
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

    private boolean isTreeSeedRecipe(RecipeInput recipe) {
        var tree = inputs.trees().get(recipe.output());
        if (tree == null || recipe.ingredients().isEmpty()) return false;
        return recipe.ingredients().stream().allMatch(ingredient -> ingredient.alternatives().stream()
                .anyMatch(option -> option.item() != recipe.output() && tree.outputs().containsKey(option.item())));
    }

    private boolean updateRecipeManufacturingCost(RecipeInput recipe, Set<Item> renewableAnchors, Set<Item> bulkRenewableItems) {
        Item resultItem = recipe.output();
        ItemValuation current = itemValues.get(resultItem);
        if (renewableAnchors.contains(resultItem) && current != null && !current.isConfigOverride()) return false;
        RecipeType<?> type = recipe.type();

        // 1. Resolve processing properties
        var typeProps = inputs.processing().properties(type);

        float totalValue = 0.0f;
        int maxIngredientDepth = 0;
        int recipeTechLevel = typeProps.techTier(); // Start at the machine's tier
        boolean isRecipeBulkFarmable = !typeProps.isManual(); // If the machine is manual, it is NOT bulk farmable

        boolean canResolve = true;

        for (IngredientInput ingredient : recipe.ingredients()) {
            ItemValuation cheapest = findCheapestIngredientCost(ingredient);

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

        if (bulkRenewableItems.contains(resultItem)) isRecipeBulkFarmable = true;
        float finalValue = (float) inputs.processing().tax(type).calculateUnitValue(totalValue, recipe.outputCount());
        int newDepth = maxIngredientDepth + 1;

        ItemValuation existingValuation = itemValues.get(resultItem);
        boolean shouldUpdateActive = false;
        boolean shouldUpdateShadow = false;

        // Float math epsilon to prevent infinite depth flip-flopping in loops
        float epsilon = 0.00001f;

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
            return true;
        }

        return false;
    }

    private ItemValuation findCheapestIngredientCost(IngredientInput ingredient) {
        float cheapestValue = Float.MAX_VALUE;
        int bestDepth = 0;
        int bestTechLevel = 0;
        boolean bestIsBulkFarmable = false; // Assume worst case initially
        boolean resolved = false;

        for (var option : ingredient.alternatives()) {
            Item item = option.item();
            ItemValuation valuation = itemValues.get(item);

            if (valuation != null) {
                float currentCost = valuation.value();

                // Remainders were captured from the ingredient stacks when the inputs were prepared.
                if (option.remainder() != null) {
                    Item remainderItem = option.remainder();
                    ItemValuation remainderValuation = itemValues.get(remainderItem);

                    if (remainderValuation != null) {
                        currentCost = Math.max(0.01f, currentCost - remainderValuation.value());
                    }
                }

                boolean shouldUpdate = false;

                if (!resolved) {
                    shouldUpdate = true;
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
                }
            }
        }

        return resolved ? new ItemValuation(cheapestValue, bestDepth, bestTechLevel, bestIsBulkFarmable) : null;
    }

}
