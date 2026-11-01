package com.holysweet.sweetbusiness.event;

import com.holysweet.sweetbusiness.command.ModCommands;
import com.holysweet.sweetbusiness.progression.CraftableSetManager;
import com.holysweet.sweetbusiness.recipe.RecipeIndex;
import com.holysweet.sweetbusiness.valuation.ValuationEngine;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public class ServerEventHandler {

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        rebuildRecipeData(event.getServer());
    }

    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            rebuildRecipeData(event.getPlayerList().getServer());
        }
    }

    private static void rebuildRecipeData(MinecraftServer server) {
        RecipeIndex.rebuild(server.getRecipeManager());
        CraftableSetManager.getInstance().invalidateAll(server);
        ValuationEngine.getInstance().calculateEconomy(server);
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        CraftableSetManager.getInstance().clear();
        RecipeIndex.clear();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher(), event.getBuildContext());
    }
}
