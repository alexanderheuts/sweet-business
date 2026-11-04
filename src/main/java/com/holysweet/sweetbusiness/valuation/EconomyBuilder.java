package com.holysweet.sweetbusiness.valuation;

import com.holysweet.sweetbusiness.config.ServerConfig;
import com.holysweet.sweetbusiness.config.ValuationConfig;
import com.holysweet.sweetbusiness.discovery.block.BlockDropDiscoverer;
import com.holysweet.sweetbusiness.discovery.block.GrassDropDiscoverer;
import com.holysweet.sweetbusiness.discovery.entity.EntityDiscoverer;
import com.holysweet.sweetbusiness.discovery.entity.EntityLootDiscoverer;
import com.holysweet.sweetbusiness.discovery.recipe.RecipeTypeDiscoverer;
import com.holysweet.sweetbusiness.discovery.recipe.RecipeTypeDiscoverer.RecipeTypeProperties;
import com.holysweet.sweetbusiness.discovery.renewable.CropDiscoverer;
import com.holysweet.sweetbusiness.discovery.renewable.TreeDiscoverer;
import com.holysweet.sweetbusiness.discovery.worldgen.StructureBlockDiscoverer;
import com.holysweet.sweetbusiness.discovery.worldgen.BlockPlacementDiscoverer;
import com.holysweet.sweetbusiness.discovery.worldgen.OreBlockDiscoverer;
import com.holysweet.sweetbusiness.discovery.worldgen.WorldGenDiscoverer;
import com.holysweet.sweetbusiness.evaluation.block.TierResolver;
import com.holysweet.sweetbusiness.evaluation.entity.EntityEvaluator;
import com.holysweet.sweetbusiness.evaluation.renewable.CropEvaluator;
import com.holysweet.sweetbusiness.evaluation.renewable.TreeEvaluator;
import com.holysweet.sweetbusiness.evaluation.worldgen.OreVolumeEvaluator;
import com.holysweet.sweetbusiness.evaluation.worldgen.TerrainClassifier;
import com.holysweet.sweetbusiness.util.Constants;
import com.holysweet.sweetbusiness.util.TagResolver;
import com.holysweet.sweetbusiness.valuation.EconomyInputs.BlockAnchors;
import com.holysweet.sweetbusiness.valuation.EconomyInputs.ConfiguredAnchor;
import com.holysweet.sweetbusiness.valuation.EconomyInputs.IngredientInput;
import com.holysweet.sweetbusiness.valuation.EconomyInputs.IngredientOption;
import com.holysweet.sweetbusiness.valuation.EconomyInputs.ProcessingSettings;
import com.holysweet.sweetbusiness.valuation.EconomyInputs.RecipeInput;
import com.holysweet.sweetbusiness.valuation.EconomyResult.AnchorSource;
import com.holysweet.sweetbusiness.valuation.block.BlockDropEvaluator;
import com.holysweet.sweetbusiness.valuation.entity.EntityDropEvaluator;
import com.holysweet.sweetbusiness.valuation.renewable.CropValuator;
import com.holysweet.sweetbusiness.valuation.renewable.TreeValuator;
import com.holysweet.sweetbusiness.valuation.worldgen.ChunkStandardEvaluator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashSet;

public final class EconomyBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(EconomyBuilder.class);

    private EconomyBuilder() {}

    public static EconomyInputs prepare(MinecraftServer server) {
        var entityDiscovery = EntityDiscoverer.discoverEntities(server);
        var entityTiers = EntityEvaluator.evaluateEntities(entityDiscovery, ServerConfig.SPAWN_EGGS_ENABLE_FARMING.get());
        var processing = captureProcessingSettings(RecipeTypeDiscoverer.discoverRecipeTypes(server));
        Map<Item, ConfiguredAnchor> configuredAnchors = new HashMap<>();
        collectConfiguredAnchors(ValuationConfig.BASE_TAGS.get(), true, configuredAnchors);
        collectConfiguredAnchors(ValuationConfig.BASE_ITEMS.get(), false, configuredAnchors);

        var worldgen = WorldGenDiscoverer.discoverFeatures(server);
        var blocks = prepareBlocks(server, worldgen);
        var placements = BlockPlacementDiscoverer.discoverBlocks(server.registryAccess(), worldgen);
        var grass = GrassDropDiscoverer.discoverDrops(server.overworld());
        var structures = StructureBlockDiscoverer.discoverBlocks(server);
        var crops = CropEvaluator.evaluateCrops(CropDiscoverer.discoverCrops(server.overworld(), placements, grass, structures));
        var cropAnchors = CropValuator.valueNaturalCrops(crops, ValuationConfig.CROP_BASELINE.get().floatValue());
        var entityLoot = EntityLootDiscoverer.discoverLoot(server, entityDiscovery.properties());
        var mobAnchors = EntityDropEvaluator.evaluateDrops(entityTiers, entityLoot, ValuationConfig.MOB_XP_VALUE_MULTIPLIER.get());

        List<RecipeInput> recipes = new ArrayList<>();
        for (var holder : server.getRecipeManager().getRecipes()) {
            var recipe = holder.value();
            var output = recipe.getResultItem(server.registryAccess());
            if (!output.isEmpty()) {
                recipes.add(new RecipeInput(holder.id(), recipe.getType(), output.getItem(), output.getCount(),
                        captureIngredients(recipe)));
            }
        }
        Set<Item> craftableItems = new HashSet<>();
        recipes.forEach(recipe -> craftableItems.add(recipe.output()));
        Set<Block> naturalBlocks = new HashSet<>(placements.sources().keySet());
        naturalBlocks.addAll(structures.sources().keySet());
        Set<ResourceLocation> naturalFeatures = new HashSet<>();
        placements.sources().values().forEach(sources -> sources.forEach(source ->
                source.configuredFeatureId().ifPresent(naturalFeatures::add)));
        var trees = TreeEvaluator.evaluateTrees(TreeDiscoverer.discoverTrees(server.overworld()),
                naturalBlocks, naturalFeatures, craftableItems);
        var treeSettings = new TreeValuator.Settings(ValuationConfig.SAPLING_BASELINE.get(), ValuationConfig.TREE_WOOD_BASELINE.get());
        var treeAnchors = TreeValuator.valueNaturalTrees(trees, treeSettings);
        return new EconomyInputs(configuredAnchors, blocks, crops, cropAnchors, trees, treeAnchors, treeSettings, mobAnchors, entityTiers,
                entityDiscovery.failures(), entityLoot.expectedDrops(), recipes, processing);
    }

    static ProcessingSettings captureProcessingSettings(Map<RecipeType<?>, RecipeTypeProperties> rules) {
        Map<Integer, Double> tierBonuses = new HashMap<>();
        ValuationConfig.TIER_PROCESSING_BONUSES.forEach((tier, bonus) -> tierBonuses.put(tier, bonus.get()));
        return new ProcessingSettings(rules, RecipeTypeDiscoverer.getDefaultProperties(),
                ValuationConfig.BASE_PROCESSING_BONUS.get(), ValuationConfig.MANUAL_PROCESSING_BONUS.get(), tierBonuses);
    }

    private static List<IngredientInput> captureIngredients(Recipe<?> recipe) {
        List<IngredientInput> ingredients = new ArrayList<>();
        for (var ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            List<IngredientOption> alternatives = new ArrayList<>();
            for (var stack : ingredient.getItems()) {
                Item remainder = stack.hasCraftingRemainingItem() ? stack.getCraftingRemainingItem().getItem() : null;
                alternatives.add(new IngredientOption(stack.getItem(), remainder));
            }
            ingredients.add(new IngredientInput(alternatives));
        }
        return List.copyOf(ingredients);
    }

    private static List<BlockAnchors> prepareBlocks(MinecraftServer server, WorldGenDiscoverer.DiscoveryResult worldgen) {
        var oreBlocks = OreBlockDiscoverer.discoverBlocks(worldgen);
        Map<Block, Set<String>> oreFeatures = new HashMap<>();
        var configuredFeatures = server.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        oreBlocks.forEach((feature, ore) -> {
            var id = configuredFeatures.getKey(feature.feature().value());
            if (id != null) ore.targetStates().forEach(state ->
                    oreFeatures.computeIfAbsent(state.getBlock(), key -> new TreeSet<>()).add(id.toString()));
        });
        var oreVolumes = OreVolumeEvaluator.evaluateVolumes(worldgen, oreBlocks);
        var blockValues = ChunkStandardEvaluator.calculateAnchorValues(oreVolumes);
        List<BlockAnchors> blocks = new ArrayList<>();
        for (var entry : blockValues.entrySet()) {
            Block block = entry.getKey();
            boolean terrain = TerrainClassifier.isBoringTerrain(block);
            String detail = String.join(";", oreFeatures.getOrDefault(block, Set.of()));
            AnchorSource source = new AnchorSource(terrain ? "terrain" : "worldgen",
                    terrain || detail.isEmpty() ? BuiltInRegistries.BLOCK.getKey(block).toString() : detail);
            Map<Item, Double> drops = Map.of();
            if (block.getLootTable() != BuiltInLootTables.EMPTY) {
                var rawDrops = BlockDropDiscoverer.getAverageDrops(server.overworld(), block);
                drops = BlockDropEvaluator.evaluateDropValues(entry.getValue(), rawDrops);
            }
            blocks.add(new BlockAnchors(block.asItem(), entry.getValue().floatValue(),
                    TierResolver.getBlockTechLevel(block.defaultBlockState()), source, drops, block.getLootTable().location()));
        }
        return List.copyOf(blocks);
    }

    public static void logResult(EconomyInputs inputs, EconomyResult result) {
        int industrialItems = (int) result.itemValues().values().stream().filter(ItemValuation::isBulkFarmable).count();
        LOGGER.info("Economy valuation completed in {} iterations.", result.iterations());
        LOGGER.info("Mapped Items: {} (Industrial: {}, Manual: {}) | Unknown Items: {}",
                result.itemValues().size(), industrialItems, result.itemValues().size() - industrialItems,
                BuiltInRegistries.ITEM.size() - result.itemValues().size());
        if (result.unresolvedRecipes() > 0) {
            LOGGER.warn("Unresolved Recipes: {} (Missing base materials or recursive loop)", result.unresolvedRecipes());
        } else {
            LOGGER.info("All loaded recipes were successfully mapped!");
        }
        logConfigDiscrepancies(inputs, result);
    }

    private static void collectConfiguredAnchors(List<? extends String> entries, boolean isTag,
                                                  Map<Item, ConfiguredAnchor> anchors) {
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
                anchors.put(item, new ConfiguredAnchor(ItemValuation.createOverride(baseValue, techLevel),
                        new AnchorSource("config", (isTag ? "#" : "") + identifier)));
            }
        }
    }

    private static void logConfigDiscrepancies(EconomyInputs inputs, EconomyResult result) {
        if (!LOGGER.isDebugEnabled()) return;

        // 1. Audit Item Valuations
        LOGGER.debug("=== Sweet Business: Config Override Audit ===");
        result.itemValues().entrySet().stream()
                .filter(entry -> entry.getValue().isConfigOverride())
                .sorted((e1, e2) -> {
                    String name1 = BuiltInRegistries.ITEM.getKey(e1.getKey()).toString();
                    String name2 = BuiltInRegistries.ITEM.getKey(e2.getKey()).toString();
                    return name1.compareTo(name2);
                })
                .forEach(entry -> {
                    String itemName = BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
                    ItemValuation valuation = entry.getValue();

                    String costStr = valuation.calculatedCost() > 0.0f ?
                            String.format("%.2f", valuation.calculatedCost()) : "BLIND SPOT (No Calculated Cost)";

                    LOGGER.debug("Item: {} | Config Value: {} | Calculated Cost: {}",
                            itemName,
                            String.format("%.2f", valuation.value()),
                            costStr);
                });
        LOGGER.debug("=============================================");

        // 2. Audit Recipe Type Rules
        LOGGER.debug("=== Sweet Business: Recipe Type Derivation Audit ===");
        inputs.processing().rules().entrySet().stream()
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
