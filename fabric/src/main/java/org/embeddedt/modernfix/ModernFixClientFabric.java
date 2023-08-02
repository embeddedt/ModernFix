package org.embeddedt.modernfix;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class ModernFixClientFabric implements ClientModInitializer {
    public static ModernFixClient commonMod;

    @Override
    public void onInitializeClient() {
        commonMod = new ModernFixClient();

        ClientTickEvents.END_CLIENT_TICK.register((mc) -> commonMod.onRenderTickEnd());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            commonMod.onServerStarted(server);
        });
    }
}
