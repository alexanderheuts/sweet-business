package com.holysweet.sweetbusiness;

import com.holysweet.sweetbusiness.item.ContractItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = SweetBusiness.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = SweetBusiness.MODID, value = Dist.CLIENT)
public class SweetBusinessClient {
    public SweetBusinessClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onItemTooltip(ItemTooltipEvent event) {
        ContractItem.getContract(event.getItemStack()).ifPresent(contract -> {
            var options = Minecraft.getInstance().options;
            Component hint = Component.translatable(contract.isComplete()
                            ? "tooltip.sweetbusiness.contract.redeem" : "tooltip.sweetbusiness.contract.deliver",
                    options.keyShift.getTranslatedKeyMessage(), options.keyUse.getTranslatedKeyMessage());
            event.getToolTip().add(hint.copy().withStyle(ChatFormatting.AQUA));
        });
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
    }
}
