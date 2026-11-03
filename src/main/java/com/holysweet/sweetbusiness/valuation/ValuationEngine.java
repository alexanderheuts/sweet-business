package com.holysweet.sweetbusiness.valuation;

import com.holysweet.sweetbusiness.valuation.block.BlockDropEvaluator;
import com.holysweet.sweetbusiness.valuation.worldgen.ChunkStandardEvaluator;
import com.holysweet.sweetbusiness.valuation.entity.EntityDropEvaluator;
import com.holysweet.sweetbusiness.evaluation.entity.EntityTierEvaluator;
import com.holysweet.sweetbusiness.valuation.recipe.ProcessingTax;

import com.holysweet.sweetbusiness.config.Config;
import com.holysweet.sweetbusiness.util.Constants;
import com.holysweet.sweetbusiness.util.TagResolver;
import com.holysweet.sweetbusiness.evaluation.block.TierResolver;
import com.holysweet.sweetbusiness.discovery.block.BlockDropDiscoverer;
import com.holysweet.sweetbusiness.discovery.entity.EntityDiscoverer;
import com.holysweet.sweetbusiness.discovery.entity.EntityLootDiscoverer;
import com.holysweet.sweetbusiness.discovery.recipe.RecipeTypeDiscoverer;
import com.holysweet.sweetbusiness.discovery.worldgen.WorldGenVolumeDiscoverer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ValuationEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValuationEngine.class);
    private static final ValuationEngine INSTANCE = new ValuationEngine();

    private final Map<Item, ItemValuation> itemValues = new HashMap<>();

    private Map<EntityType<?>, EntityTierEvaluator.EntityTier> entityTiers = Map.of();
    private Map<EntityType<?>, String> unidentifiedEntities = Map.of();
    private Map<EntityType<?>, Map<Item, Double>> entityDrops = Map.of();

    // In-memory cache of resolved RecipeTypes passed from the discoverer
    private Map<RecipeType<?>, RecipeTypeDiscoverer.RecipeTypeProperties> processingRules = new HashMap<>();

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

    public Map<EntityType<?>, EntityTierEvaluator.EntityTier> getEntityTiers() {
        return entityTiers;
    }

    public Map<EntityType<?>, String> getUnidentifiedEntities() {
        return unidentifiedEntities;
    }

    public Map<EntityType<?>, Map<Item, Double>> getEntityDrops() {
        return entityDrops;
    }

    public Map<RecipeType<?>, RecipeTypeDiscoverer.RecipeTypeProperties> getProcessingRules() {
        return Map.copyOf(processingRules);
    }

    public ProcessingTax getProcessingTax(RecipeType<?> type) {
        var properties = getProcessingProperties(type);
        return new ProcessingTax(Config.BASE_PROCESSING_BONUS.get(),
                Config.TIER_PROCESSING_BONUSES.get(properties.techTier()).get(),
                properties.isManual() ? Config.MANUAL_PROCESSING_BONUS.get() : 0.0);
    }

    private RecipeTypeDiscoverer.RecipeTypeProperties getProcessingProperties(RecipeType<?> type) {
        var properties = processingRules.get(type);
        return properties != null ? properties : RecipeTypeDiscoverer.getDefaultProperties();
    }

    /**
     * Executes the full economy discovery and calculation process.
     */
    public void calculateEconomy(MinecraftServer server) {
        itemValues.clear();

        var entityDiscovery = EntityDiscoverer.discoverEntities(server);
        entityTiers = EntityTierEvaluator.calculateTiers(entityDiscovery, Config.SPAWN_EGGS_ENABLE_FARMING.get());
        unidentifiedEntities = entityDiscovery.failures();

        // Load processing tiers and manual flags mapped from Config
        this.processingRules = RecipeTypeDiscoverer.discoverRecipeTypes(server);

        // First set configured values
        seedAnchors();

        // Handle block drops as initial seed (i.e. redstone ore -> redstone)
        Set<Item> worldgenAnchors = evaluateBlockDrops(server);
        evaluateEntityDrops(server, worldgenAnchors);

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

        logConfigDiscrepancies();
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
                // Config values are Override values. Your ItemValuation.createOverride defaults isBulkFarmable to false.
                itemValues.put(item, ItemValuation.createOverride(baseValue, techLevel));
            }
        }
    }

    private Set<Item> evaluateBlockDrops(MinecraftServer server) {
        Set<Item> worldgenAnchors = new HashSet<>();
        Map<Block, Double> oreVolumes = WorldGenVolumeDiscoverer.discoverOreVolumes(server);
        Map<Block, Double> blockValues = ChunkStandardEvaluator.calculateAnchorValues(oreVolumes);

        net.minecraft.server.level.ServerLevel overworld = server.overworld();

        for (Map.Entry<Block, Double> entry : blockValues.entrySet()) {
            Block block = entry.getKey();
            float blockValue = entry.getValue().floatValue();
            int techLevel = TierResolver.getBlockTechLevel(block.defaultBlockState());

            // 1. Incorporate the block itself into the item list as a manual anchor
            Item blockItem = block.asItem();
            if (blockItem != Items.AIR) {
                worldgenAnchors.add(blockItem);
                ItemValuation existingBlock = itemValues.get(blockItem);
                if (existingBlock != null) {
                    if (existingBlock.isConfigOverride()) {
                        itemValues.put(blockItem, existingBlock.withCalculatedValue(blockValue));
                    }
                } else {
                    // Mined blocks are inherently manual to gather (isBulkFarmable = false)
                    itemValues.put(blockItem, new ItemValuation(blockValue, 0, techLevel, false));
                }
            }

            if (block.getLootTable() == BuiltInLootTables.EMPTY) {
                continue;
            }

            // 2. Discover raw drops using Monte Carlo simulation
            Map<Item, Double> rawDrops = BlockDropDiscoverer.getAverageDrops(overworld, block);

            // 3. Evaluate the economic values of those drops
            Map<Item, Double> evaluatedDrops = BlockDropEvaluator.evaluateDropValues(entry.getValue(), rawDrops);

            // 4. Safely store the results, updating shadow values for overrides
            for (Map.Entry<Item, Double> dropEntry : evaluatedDrops.entrySet()) {
                Item droppedItem = dropEntry.getKey();
                worldgenAnchors.add(droppedItem);
                float dropValue = dropEntry.getValue().floatValue();

                ItemValuation existingDrop = itemValues.get(droppedItem);

                if (existingDrop != null) {
                    if (existingDrop.isConfigOverride()) {
                        itemValues.put(droppedItem, existingDrop.withCalculatedValue(dropValue));
                    }
                } else {
                    itemValues.put(droppedItem, new ItemValuation(dropValue, 0, techLevel, false));
                }
            }
        }
        return worldgenAnchors;
    }

    private void evaluateEntityDrops(MinecraftServer server, Set<Item> worldgenAnchors) {
        var discovery = EntityLootDiscoverer.discoverLoot(server, entityTiers);
        entityDrops = discovery.expectedDrops();
        var anchors = EntityDropEvaluator.evaluateDrops(entityTiers, discovery, Config.MOB_XP_VALUE_MULTIPLIER.get());
        anchors.forEach((item, candidate) -> {
            ItemValuation existing = itemValues.get(item);
            if (existing == null) {
                itemValues.put(item, candidate);
                return;
            }

            boolean bulkFarmable = existing.isBulkFarmable() || candidate.isBulkFarmable();
            ItemValuation merged = existing;
            if (!worldgenAnchors.contains(item)) {
                if (existing.isConfigOverride()) {
                    merged = existing.withCalculatedValue(candidate.value());
                } else if (candidate.value() < existing.value()
                        || (candidate.value() == existing.value() && candidate.techLevel() < existing.techLevel())) {
                    merged = candidate;
                }
            }
            itemValues.put(item, merged.withBulkFarmable(bulkFarmable));
        });
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
        RecipeType<?> type = holder.value().getType();

        // 1. Resolve processing properties
        var typeProps = getProcessingProperties(type);

        float totalValue = 0.0f;
        int maxIngredientDepth = 0;
        int recipeTechLevel = typeProps.techTier(); // Start at the machine's tier
        boolean isRecipeBulkFarmable = !typeProps.isManual(); // If the machine is manual, it is NOT bulk farmable

        boolean canResolve = true;

        for (Ingredient ingredient : holder.value().getIngredients()) {
            if (ingredient.isEmpty()) continue;

            ItemValuation cheapest = findCheapestIngredient(ingredient);

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

        float finalValue = (float) getProcessingTax(type).calculateUnitValue(totalValue, resultStack.getCount());
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
                if (existingValuation.calculatedValue() == 0.0f || finalValue < existingValuation.calculatedValue() - epsilon) {
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
            itemValues.put(resultItem, existingValuation.withCalculatedValue(finalValue));
            return true; // Return true so subsequent recipes using the shadow value also update
        } else if (shouldUpdateActive) {
            itemValues.put(resultItem, new ItemValuation(finalValue, newDepth, recipeTechLevel, isRecipeBulkFarmable));
            return true;
        }

        return false;
    }

    private ItemValuation findCheapestIngredient(Ingredient ingredient) {
        float cheapestValue = Float.MAX_VALUE;
        int bestDepth = 0;
        int bestTechLevel = 0;
        boolean bestIsBulkFarmable = false; // Assume worst case initially
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

    private void logConfigDiscrepancies() {
        if (!LOGGER.isDebugEnabled()) return;

        // 1. Audit Item Valuations
        LOGGER.debug("=== Sweet Business: Config Override Audit ===");
        itemValues.entrySet().stream()
                .filter(entry -> entry.getValue().isConfigOverride())
                .sorted((e1, e2) -> {
                    String name1 = BuiltInRegistries.ITEM.getKey(e1.getKey()).toString();
                    String name2 = BuiltInRegistries.ITEM.getKey(e2.getKey()).toString();
                    return name1.compareTo(name2);
                })
                .forEach(entry -> {
                    String itemName = BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
                    ItemValuation valuation = entry.getValue();

                    String mathStr = valuation.calculatedValue() > 0.0f ?
                            String.format("%.2f", valuation.calculatedValue()) : "BLIND SPOT (No Math)";

                    LOGGER.debug("Item: {} | Config Value: {} | Engine Math: {}",
                            itemName,
                            String.format("%.2f", valuation.value()),
                            mathStr);
                });
        LOGGER.debug("=============================================");

        // 2. Audit Recipe Type Rules
        LOGGER.debug("=== Sweet Business: Recipe Type Derivation Audit ===");
        processingRules.entrySet().stream()
                .filter(entry -> {
                    RecipeTypeDiscoverer.TierSource source = entry.getValue().source();
                    return source == RecipeTypeDiscoverer.TierSource.NAMESPACE || source == RecipeTypeDiscoverer.TierSource.GLOBAL_DEFAULT;
                })
                .sorted((e1, e2) -> {
                    String name1 = String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(e1.getKey()));
                    String name2 = String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(e2.getKey()));
                    return name1.compareTo(name2);
                })
                .forEach(entry -> {
                    String typeName = String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(entry.getKey()));
                    RecipeTypeDiscoverer.RecipeTypeProperties props = entry.getValue();

                    LOGGER.debug("Recipe Type: {} | Tier: {} | Source: {} | Matched Rule: {}",
                            typeName,
                            props.techTier(),
                            props.source().name(),
                            props.matchedRule());
                });
        LOGGER.debug("=====================================================");
    }
}
