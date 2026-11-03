package com.holysweet.sweetbusiness.valuation;

import com.holysweet.sweetbusiness.discovery.recipe.RecipeTypeDiscoverer.RecipeTypeProperties;
import com.holysweet.sweetbusiness.evaluation.entity.EntityTierEvaluator.EntityTier;
import com.holysweet.sweetbusiness.evaluation.renewable.CropEvaluator.CropEvaluation;
import com.holysweet.sweetbusiness.valuation.EconomyResult.AnchorSource;
import com.holysweet.sweetbusiness.valuation.entity.EntityDropEvaluator.DropAnchor;
import com.holysweet.sweetbusiness.valuation.recipe.ProcessingTax;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record EconomyInputs(Map<Item, ConfiguredAnchor> configuredAnchors, List<BlockAnchors> blocks,
                            Map<Item, CropEvaluation> crops, Map<Item, ItemValuation> naturalCropAnchors,
                            Map<Item, DropAnchor> mobAnchors, Map<EntityType<?>, EntityTier> entityTiers,
                            Map<EntityType<?>, String> unidentifiedEntities,
                            Map<EntityType<?>, Map<Item, Double>> entityDrops,
                            List<RecipeInput> recipes, ProcessingSettings processing) {

    public EconomyInputs {
        configuredAnchors = Map.copyOf(configuredAnchors);
        blocks = List.copyOf(blocks);
        crops = Map.copyOf(crops);
        naturalCropAnchors = Map.copyOf(naturalCropAnchors);
        mobAnchors = Map.copyOf(mobAnchors);
        entityTiers = Map.copyOf(entityTiers);
        unidentifiedEntities = Map.copyOf(unidentifiedEntities);
        Map<EntityType<?>, Map<Item, Double>> drops = new HashMap<>();
        entityDrops.forEach((type, values) -> drops.put(type, Map.copyOf(values)));
        entityDrops = Map.copyOf(drops);
        recipes = List.copyOf(recipes);
    }

    public record ConfiguredAnchor(ItemValuation value, AnchorSource source) {}

    public record BlockAnchors(Item item, float value, int techLevel, AnchorSource source,
                               Map<Item, Double> drops, ResourceLocation lootTableId) {
        public BlockAnchors {
            drops = Map.copyOf(drops);
        }
    }

    public record RecipeInput(ResourceLocation id, RecipeType<?> type, Item output, int outputCount,
                              List<IngredientInput> ingredients) {
        public RecipeInput {
            ingredients = List.copyOf(ingredients);
        }
    }

    public record IngredientInput(List<IngredientOption> alternatives) {
        public IngredientInput {
            alternatives = List.copyOf(alternatives);
        }
    }

    public record IngredientOption(Item item, @Nullable Item remainder) {}

    public record ProcessingSettings(Map<RecipeType<?>, RecipeTypeProperties> rules,
                                     RecipeTypeProperties defaults, double baseBonus, double manualBonus,
                                     Map<Integer, Double> tierBonuses) {
        public ProcessingSettings {
            rules = Map.copyOf(rules);
            tierBonuses = Map.copyOf(tierBonuses);
        }

        public RecipeTypeProperties properties(RecipeType<?> type) {
            return rules.getOrDefault(type, defaults);
        }

        public ProcessingTax tax(RecipeType<?> type) {
            RecipeTypeProperties properties = properties(type);
            return new ProcessingTax(baseBonus, tierBonuses.get(properties.techTier()),
                    properties.isManual() ? manualBonus : 0.0);
        }
    }
}
