package com.holysweet.sweetbusiness;

import com.holysweet.sweetbusiness.config.ServerConfig;
import com.holysweet.sweetbusiness.config.TierConfig;
import com.holysweet.sweetbusiness.config.ValuationConfig;
import com.holysweet.sweetbusiness.event.ServerEventHandler;
import com.holysweet.sweetbusiness.event.LedgerEventHandler;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

@Mod(SweetBusiness.MODID)
public class SweetBusiness {

    public static final String MODID = "sweetbusiness";

    public SweetBusiness(IEventBus modEventBus, ModContainer modContainer) {

        NeoForge.EVENT_BUS.register(new ServerEventHandler());
        NeoForge.EVENT_BUS.register(new LedgerEventHandler());

        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC, MODID + "/" + MODID + "-server.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, TierConfig.SPEC, MODID + "/" + MODID + "-tiers.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, ValuationConfig.SPEC, MODID + "/" + MODID + "-valuation.toml");

    }
}
