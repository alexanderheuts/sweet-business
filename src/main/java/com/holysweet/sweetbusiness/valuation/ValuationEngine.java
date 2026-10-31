package com.holysweet.sweetbusiness.valuation;

import com.holysweet.sweetbusiness.config.Config;
import com.holysweet.sweetbusiness.util.Constants;
import com.holysweet.sweetbusiness.util.TagResolver;
import com.holysweet.sweetbusiness.util.TierResolver;
import com.holysweet.sweetbusiness.valuation.discovery.BlockDropDiscoverer;
import com.holysweet.sweetbusiness.valuation.discovery.WorldGenVolumeDiscoverer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ValuationEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValuationEngine.class);
    private static final ValuationEngine INSTANCE = new ValuationEngine();

    private final Map<Item, ItemValuation> itemValues = new HashMap<>();

    private ValuationEngine() {
        // Enforce Singleton
    }

    public static ValuationEngine getInstance() {
        return INSTANCE;
    }

    /**
     * Safely fetch a valuation for any item. Returns UNKNOWN if not found.
     */
    public ItemValuation getValuation(Item item) {
        return itemValues.getOrDefault(item, ItemValuation.UNKNOWN);
    }

    /**
     * Executes the full economy discovery and calculation process.
     */
    public void calculateEconomy(MinecraftServer server) {
        itemValues.clear();

        // First set configured values
        seedAnchors();

        // Handle block drops as initial seed (i.e. redstone ore -> redstone)
        evaluateBlockDrops(server);

        RegistryAccess registryAccess = server.registryAccess();
        int iteration = runValuationLoop(server, registryAccess);

        int totalItems = net.minecraft.core.registries.BuiltInRegistries.ITEM.size();
        int unknownItems = totalItems - itemValues.size();

        int industrialItems = 0;
        int manualItems = 0;

        for (ItemValuation valuation : itemValues.values()) {
            if (valuation.isBulkFarmable()) {
                industrialItems++;
            } else {
                manualItems++;
            }
        }

        int unresolvedRecipes = 0;
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            ItemStack resultStack = holder.value().getResultItem(registryAccess);
            if (!resultStack.isEmpty() && !getValuation(resultStack.getItem()).isKnown()) {
                unresolvedRecipes++;
            }
        }

        LOGGER.info("Economy valuation completed in {} iterations.", iteration);
        LOGGER.info("Mapped Items: {} (Industrial: {}, Manual: {}) | Unknown Items: {}",
                itemValues.size(), industrialItems, manualItems, unknownItems);

        if (unresolvedRecipes > 0) {
            LOGGER.warn("Unresolved Recipes: {} (Missing base materials or recursive loop)", unresolvedRecipes);
        } else {
            LOGGER.info("All loaded recipes were successfully mapped!");
        }
    }

    private void seedAnchors() {
        // 1. Load broad categories first to set the baseline
        seedFromConfigList(Config.BASE_TAGS.get(), true);

        // 2. Load individual items second so they strictly override tag values
        seedFromConfigList(Config.BASE_ITEMS.get(), false);
    }

    private void seedFromConfigList(List<? extends String> entries, boolean isTag) {
        for (String entry : entries) {
            String[] parts = entry.split("=");
            if (parts.length != 2) continue;

            String identifier = parts[0];
            String[] valParts = parts[1].split(";");

            float baseValue;
            int techLevel = Constants.BASE_TECH_LEVEL;

            try {
                baseValue = Float.parseFloat(valParts[0]);
                if (valParts.length > 1) {
                    techLevel = Integer.parseInt(valParts[1]);
                }
            } catch (NumberFormatException e) {
                continue;
            }

            Set<Item> resolvedItems = isTag ? TagResolver.resolveTag(identifier) : TagResolver.resolveItem(identifier);

            for (Item item : resolvedItems) {
                itemValues.put(item, new ItemValuation(baseValue, 0, techLevel, false));
            }
        }
    }

    /**
     * Takes the finalized block values, registers the blocks themselves,
     * and parses their Loot Tables to price the actual drops.
     */
    private void evaluateBlockDrops(MinecraftServer server) {
        Map<Block, Double> oreVolumes = WorldGenVolumeDiscoverer.discoverOreVolumes(server);
        Map<Block, Double> blockValues = ChunkStandardEvaluator.calculateAnchorValues(oreVolumes);

        net.minecraft.server.level.ServerLevel overworld = server.overworld();

        for (Map.Entry<Block, Double> entry : blockValues.entrySet()) {
            Block block = entry.getKey();
            double blockValue = entry.getValue();
            int techLevel = TierResolver.getBlockTechLevel(block.defaultBlockState());

            // 1. Incorporate the block itself into the item list as a manual anchor
            Item blockItem = block.asItem();
            if (blockItem != Items.AIR) {
                if (!itemValues.containsKey(blockItem)) {
                    itemValues.put(blockItem, new ItemValuation((float) blockValue, 0, techLevel, false));
                }
            }

            if (block.getLootTable() == BuiltInLootTables.EMPTY) {
                continue;
            }

            // 2. Discover raw drops using Monte Carlo simulation
            Map<Item, Double> rawDrops = BlockDropDiscoverer.getAverageDrops(overworld, block);

            // 3. Evaluate the economic values of those drops
            Map<Item, Double> evaluatedDrops = BlockDropEvaluator.evaluateDropValues(blockValue, rawDrops);

            // 4. Safely store the results, respecting manual Config overrides
            for (Map.Entry<Item, Double> dropEntry : evaluatedDrops.entrySet()) {
                Item droppedItem = dropEntry.getKey();

                if (!itemValues.containsKey(droppedItem)) {
                    float dropValue = dropEntry.getValue().floatValue();
                    itemValues.put(droppedItem, new ItemValuation(dropValue, 0, techLevel, false));
                }
            }
        }
    }

    private int runValuationLoop(MinecraftServer server, RegistryAccess registryAccess) {
        boolean changed = true;
        int iteration = 0;

        while (changed && iteration < Constants.ENGINE_MAX_ITERATIONS) {
            changed = false;
            iteration++;

            for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
                if (evaluateRecipe(holder, registryAccess)) {
                    changed = true;
                }
            }
        }
        return iteration;
    }

    private boolean evaluateRecipe(RecipeHolder<?> holder, RegistryAccess registryAccess) {
        ItemStack resultStack = holder.value().getResultItem(registryAccess);
        if (resultStack.isEmpty()) return false;

        Item resultItem = resultStack.getItem();

        float totalValue = 0;
        int maxIngredientDepth = 0;
        int recipeTechLevel = Constants.BASE_TECH_LEVEL;
        boolean canResolve = true;
        boolean isRecipeBulkFarmable = true;

        for (Ingredient ingredient : holder.value().getIngredients()) {
            if (ingredient.isEmpty()) continue;

            ItemValuation cheapest = findCheapestIngredient(ingredient);

            if (cheapest == null) {
                canResolve = false;
                break;
            }

            totalValue += cheapest.value();
            recipeTechLevel = Math.max(recipeTechLevel, cheapest.techLevel());
            maxIngredientDepth = Math.max(maxIngredientDepth, cheapest.depth());

            if (!cheapest.isBulkFarmable()) {
                isRecipeBulkFarmable = false;
            }
        }

        if (canResolve) {
            float finalValue = (totalValue * Constants.DEFAULT_RECIPE_MULTIPLIER) / resultStack.getCount();
            int newDepth = maxIngredientDepth + 1;

            ItemValuation existingValuation = itemValues.get(resultItem);
            boolean shouldUpdate = false;

            if (existingValuation == null) {
                shouldUpdate = true;
            } else {
                if (finalValue < existingValuation.value()) {
                    shouldUpdate = true;
                } else if (!existingValuation.isBulkFarmable() && isRecipeBulkFarmable) {
                    shouldUpdate = true;
                }
            }

            if (shouldUpdate) {
                itemValues.put(resultItem, new ItemValuation(finalValue, newDepth, recipeTechLevel, isRecipeBulkFarmable));
                return true;
            }
        }

        return false;
    }

    private ItemValuation findCheapestIngredient(Ingredient ingredient) {
        float cheapestValue = Float.MAX_VALUE;
        int bestDepth = 0;
        int bestTechLevel = 0;
        boolean bestIsBulkFarmable = false;
        boolean resolved = false;

        for (ItemStack stack : ingredient.getItems()) {
            Item item = stack.getItem();
            ItemValuation valuation = itemValues.get(item);

            if (valuation != null) {
                float currentCost = valuation.value();

                // Handle Recipe Remainders using the stack context
                if (stack.hasCraftingRemainingItem()) {
                    ItemStack remainderStack = stack.getCraftingRemainingItem();
                    Item remainderItem = remainderStack.getItem();
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
                    // Override: It might cost slightly more, but this variant is automated
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