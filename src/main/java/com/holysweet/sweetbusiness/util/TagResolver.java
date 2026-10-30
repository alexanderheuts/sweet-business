package com.holysweet.sweetbusiness.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class TagResolver {

    private TagResolver() {
    }

    /**
     * Resolves a direct item identifier into an Item Set (containing 0 or 1 item).
     */
    public static Set<Item> resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) return Collections.emptySet();

        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location == null) return Collections.emptySet();

        Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(location);

        return itemOpt.map(Set::of).orElse(Collections.emptySet());

    }

    /**
     * Resolves a tag identifier into a Set of all matching Items.
     */
    public static Set<Item> resolveTag(String tagId) {
        if (tagId == null || tagId.isBlank()) return Collections.emptySet();

        ResourceLocation location = ResourceLocation.tryParse(tagId);
        if (location == null) return Collections.emptySet();

        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, location);
        Set<Item> resolvedItems = new HashSet<>();

        BuiltInRegistries.ITEM.getTag(tagKey).ifPresent(namedTag ->
                namedTag.forEach(holder -> resolvedItems.add(holder.value()))
        );

        return resolvedItems;
    }
}