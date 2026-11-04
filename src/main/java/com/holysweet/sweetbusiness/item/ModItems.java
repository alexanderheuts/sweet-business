package com.holysweet.sweetbusiness.item;

import com.holysweet.sweetbusiness.SweetBusiness;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SweetBusiness.MODID);
    public static final DeferredItem<ContractItem> CONTRACT = ITEMS.registerItem("contract",
            ContractItem::new, new Item.Properties().stacksTo(1));

    private ModItems() {}
}
