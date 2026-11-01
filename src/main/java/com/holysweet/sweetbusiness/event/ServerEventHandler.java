package com.holysweet.sweetbusiness.event;

import com.holysweet.sweetbusiness.command.ModCommands;
import com.holysweet.sweetbusiness.recipe.RecipeIndex;
import com.holysweet.sweetbusiness.valuation.ValuationEngine;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public class ServerEventHandler {

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        RecipeIndex.rebuild(event.getServer().getRecipeManager());
        ValuationEngine.getInstance().calculateEconomy(event.getServer());
    }

    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            RecipeIndex.rebuild(event.getPlayerList().getServer().getRecipeManager());
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        RecipeIndex.clear();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher(), event.getBuildContext());
    }
}
