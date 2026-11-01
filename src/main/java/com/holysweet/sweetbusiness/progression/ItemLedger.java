package com.holysweet.sweetbusiness.progression;

import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ItemLedger {

    private final UUID ownerId;
    private final Set<ResourceLocation> discoveredItems;
    // Derived craftability is not persisted, so a loaded ledger also starts dirty.
    private boolean dirty = true;

    ItemLedger(UUID ownerId) {
        this(ownerId, Set.of());
    }

    ItemLedger(UUID ownerId, Set<ResourceLocation> discoveredItems) {
        this.ownerId = ownerId;
        this.discoveredItems = new HashSet<>(discoveredItems);
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Set<ResourceLocation> getDiscoveredItems() {
        return Set.copyOf(discoveredItems);
    }

    public int getDiscoveredItemCount() {
        return discoveredItems.size();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }

    void markDirty() {
        dirty = true;
    }

    boolean discover(ResourceLocation itemId) {
        if (!discoveredItems.add(itemId)) return false;

        dirty = true;
        return true;
    }
}
