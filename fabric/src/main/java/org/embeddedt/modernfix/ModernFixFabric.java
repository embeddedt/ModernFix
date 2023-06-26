package org.embeddedt.modernfix;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.lang.ref.WeakReference;

public class ModernFixFabric implements ModInitializer {
    private ModernFix commonMod;
    public static WeakReference<MinecraftServer> theServer = new WeakReference<>(null);
    @Override
    public void onInitialize() {
        commonMod = new ModernFix();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            theServer = new WeakReference<>(server);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            commonMod.onServerStarted();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            commonMod.onServerDead(server);
        });

        // TODO: implement entity ID desync
    }


}
