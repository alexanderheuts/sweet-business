package com.holysweet.sweetbusiness.valuation;

import com.holysweet.sweetbusiness.config.Config;
import com.holysweet.sweetbusiness.util.Constants;
import com.holysweet.sweetbusiness.util.TagResolver;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
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
        seedAnchors();

        RegistryAccess registryAccess = server.registryAccess();
        int iteration = runValuationLoop(server, registryAccess);

        int totalItems = net.minecraft.core.registries.BuiltInRegistries.ITEM.size();
        int unknownItems = totalItems - itemValues.size();

        int unresolvedRecipes = 0;
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            ItemStack resultStack = holder.value().getResultItem(registryAccess);
            if (!resultStack.isEmpty() && !getValuation(resultStack.getItem()).isKnown()) {
                unresolvedRecipes++;
            }
        }

        LOGGER.info("Economy valuation completed in {} iterations.", iteration);
        LOGGER.info("Mapped Items: {} | Unknown Items: {}", itemValues.size(), unknownItems);
        if (unresolvedRecipes > 0) {
            LOGGER.warn("Unresolved Recipes: {} (Missing base materials or recursive loop)", unresolvedRecipes);
        } else {
            LOGGER.info("All loaded recipes were successfully mapped!");
        }
    }

    private void seedAnchors() {
        seedFromConfigList(Config.BASE_ITEMS.get(), false);
        seedFromConfigList(Config.BASE_TAGS.get(), true);
    }

    private void seedFromConfigList(List<? extends String> entries, boolean isTag) {
        for (String entry : entries) {
            String[] parts = entry.split("=");
            if (parts.length != 2) continue;

            String identifier = parts[0];
            float baseValue;

            try {
                baseValue = Float.parseFloat(parts[1]);
            } catch (NumberFormatException e) {
                continue;
            }

            Set<Item> resolvedItems = isTag ? TagResolver.resolveTag(identifier) : TagResolver.resolveItem(identifier);

            for (Item item : resolvedItems) {
                itemValues.put(item, new ItemValuation(baseValue, 0, Constants.BASE_TECH_LEVEL));
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

        for (Ingredient ingredient : holder.value().getIngredients()) {
            if (ingredient.isEmpty()) continue;

            ItemValuation cheapestValid = findCheapestIngredient(ingredient);
            if (!cheapestValid.isKnown()) {
                return false;
            }

            totalValue += cheapestValid.value();
            maxIngredientDepth = Math.max(maxIngredientDepth, cheapestValid.depth());
            recipeTechLevel = Math.max(recipeTechLevel, cheapestValid.techLevel());
        }

        if (totalValue <= 0) return false;

        float finalValue = (totalValue * Constants.DEFAULT_RECIPE_MULTIPLIER) / resultStack.getCount();
        int finalDepth = maxIngredientDepth + 1;

        ItemValuation currentValuation = getValuation(resultItem);

        if (!currentValuation.isKnown() || finalValue < currentValuation.value()) {
            itemValues.put(resultItem, new ItemValuation(finalValue, finalDepth, recipeTechLevel));
            return true;
        }

        return false;
    }

    private ItemValuation findCheapestIngredient(Ingredient ingredient) {
        ItemValuation best = ItemValuation.UNKNOWN;

        for (ItemStack stack : ingredient.getItems()) {
            ItemValuation val = getValuation(stack.getItem());

            if (val.isKnown()) {
                if (!best.isKnown() || val.value() < best.value()) {
                    best = val;
                }
            }
        }
        return best;
    }
}