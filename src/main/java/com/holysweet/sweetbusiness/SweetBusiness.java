package com.holysweet.sweetbusiness;

import com.holysweet.sweetbusiness.config.Config;
import com.holysweet.sweetbusiness.event.ServerEventHandler;

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

        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);

    }
}
