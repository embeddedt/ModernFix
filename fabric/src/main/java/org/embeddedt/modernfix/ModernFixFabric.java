package org.embeddedt.modernfix;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

public class ModernFixFabric implements ModInitializer {
    private ModernFix commonMod;
    public static MinecraftServer theServer;
    @Override
    public void onInitialize() {
        commonMod = new ModernFix();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            theServer = server;
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            commonMod.onServerStarted();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            commonMod.onServerDead(server);
            theServer = null;
        });

        // TODO: implement entity ID desync
    }


}
