package org.embeddedt.modernfix;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.embeddedt.modernfix.fabric.datagen.RuntimeDatagen;

public class ModernFixClientFabric implements ClientModInitializer {
    public static ModernFixClient commonMod;

    @Override
    public void onInitializeClient() {
        commonMod = new ModernFixClient();

        ClientTickEvents.END_CLIENT_TICK.register((mc) -> commonMod.onRenderTickEnd());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            commonMod.onServerStarted(server);
        });
        org.embeddedt.modernfix.fabric.modfixs.DecorativeBlocksFix.fix();
        if(FabricLoader.getInstance().isModLoaded("fabric-data-generation-api-v1")) {
            RuntimeDatagen.init();
        }
    }
}
