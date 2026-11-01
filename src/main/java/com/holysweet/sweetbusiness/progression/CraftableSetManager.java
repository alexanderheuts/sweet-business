package com.holysweet.sweetbusiness.progression;

import com.holysweet.sweetbusiness.progression.CraftableSetCalculator.RecipeRequirements;
import com.holysweet.sweetbusiness.recipe.RecipeIndex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CraftableSetManager {

    private static final CraftableSetManager INSTANCE = new CraftableSetManager();

    private final Map<UUID, Set<ResourceLocation>> craftableSets = new HashMap<>();

    private CraftableSetManager() {}

    public static CraftableSetManager getInstance() {
        return INSTANCE;
    }

    public Set<ResourceLocation> getCraftableItems(MinecraftServer server, UUID ownerId) {
        ItemLedger ledger = LedgerSavedData.get(server).getLedger(ownerId);
        Set<ResourceLocation> cachedItems = craftableSets.get(ownerId);
        if (cachedItems != null && !ledger.isDirty()) return cachedItems;

        Set<ResourceLocation> items = CraftableSetCalculator.calculate(ledger.getDiscoveredItems(),
                RecipeIndex.getInstance(), recipeId -> server.getRecipeManager().byKey(recipeId)
                        .flatMap(holder -> readRequirements(server, holder)));
        craftableSets.put(ownerId, items);
        ledger.markClean();
        return items;
    }

    public void invalidateAll(MinecraftServer server) {
        clear();
        LedgerSavedData.get(server).markLedgersDirty();
    }

    public void clear() {
        craftableSets.clear();
    }

    private static Optional<RecipeRequirements> readRequirements(MinecraftServer server, RecipeHolder<?> holder) {
        ItemStack output = holder.value().getResultItem(server.registryAccess());
        if (output.isEmpty()) return Optional.empty();

        List<Set<ResourceLocation>> ingredients = new ArrayList<>();
        for (Ingredient ingredient : holder.value().getIngredients()) {
            if (ingredient.isEmpty()) continue;
            if (ingredient.hasNoItems()) return Optional.empty();

            Set<ResourceLocation> alternatives = new HashSet<>();
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.isEmpty()) {
                    alternatives.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                }
            }
            if (alternatives.isEmpty()) return Optional.empty();
            ingredients.add(alternatives);
        }

        if (ingredients.isEmpty()) return Optional.empty();
        return Optional.of(new RecipeRequirements(BuiltInRegistries.ITEM.getKey(output.getItem()), ingredients));
    }
}
