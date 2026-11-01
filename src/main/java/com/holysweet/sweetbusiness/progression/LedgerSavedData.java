package com.holysweet.sweetbusiness.progression;

import com.holysweet.sweetbusiness.SweetBusiness;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LedgerSavedData extends SavedData {

    private static final String DATA_NAME = SweetBusiness.MODID + "_ledgers";
    private static final Factory<LedgerSavedData> FACTORY = new Factory<>(LedgerSavedData::new, LedgerSavedData::load);

    private final Map<UUID, ItemLedger> ledgers = new HashMap<>();

    public static LedgerSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public ItemLedger getLedger(UUID ownerId) {
        return ledgers.computeIfAbsent(ownerId, ItemLedger::new);
    }

    public boolean recordItem(UUID ownerId, ResourceLocation itemId) {
        if (!getLedger(ownerId).discover(itemId)) return false;

        setDirty();
        return true;
    }

    private static LedgerSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        LedgerSavedData data = new LedgerSavedData();
        ListTag savedLedgers = tag.getList("ledgers", Tag.TAG_COMPOUND);
        for (int i = 0; i < savedLedgers.size(); i++) {
            CompoundTag savedLedger = savedLedgers.getCompound(i);
            if (!savedLedger.hasUUID("owner")) continue;

            UUID ownerId = savedLedger.getUUID("owner");
            Set<ResourceLocation> items = new HashSet<>();
            ListTag savedItems = savedLedger.getList("items", Tag.TAG_STRING);
            for (int j = 0; j < savedItems.size(); j++) {
                ResourceLocation itemId = ResourceLocation.tryParse(savedItems.getString(j));
                if (itemId != null) {
                    items.add(itemId);
                }
            }
            data.ledgers.put(ownerId, new ItemLedger(ownerId, items));
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        ListTag savedLedgers = new ListTag();
        for (ItemLedger ledger : ledgers.values()) {
            CompoundTag savedLedger = new CompoundTag();
            savedLedger.putUUID("owner", ledger.getOwnerId());

            ListTag items = new ListTag();
            for (ResourceLocation itemId : ledger.getDiscoveredItems()) {
                items.add(StringTag.valueOf(itemId.toString()));
            }
            savedLedger.put("items", items);
            savedLedgers.add(savedLedger);
        }
        tag.put("ledgers", savedLedgers);
        return tag;
    }
}
