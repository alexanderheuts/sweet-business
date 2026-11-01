package com.holysweet.sweetbusiness.event;

import com.holysweet.sweetbusiness.command.ModCommands;
import com.holysweet.sweetbusiness.valuation.ValuationEngine;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

public class ServerEventHandler {

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ValuationEngine.getInstance().calculateEconomy(event.getServer());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher(), event.getBuildContext());
    }
}