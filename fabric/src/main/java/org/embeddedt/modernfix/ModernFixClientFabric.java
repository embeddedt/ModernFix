package org.embeddedt.modernfix;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.embeddedt.modernfix.fabric.datagen.RuntimeDatagen;

import java.util.concurrent.atomic.AtomicBoolean;

public class ModernFixClientFabric implements ClientModInitializer {
    public static ModernFixClient commonMod;

    @Override
    public void onInitializeClient() {
        commonMod = new ModernFixClient();

        ClientTickEvents.END_CLIENT_TICK.register((mc) -> commonMod.onRenderTickEnd());
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            AtomicBoolean hasOpened = new AtomicBoolean(false);
            ScreenEvents.beforeTick(screen).register(screen1 -> {
                if(Minecraft.getInstance().getOverlay() != null)
                    return;
                if(!hasOpened.getAndSet(true)) {
                    commonMod.onScreenOpening(screen1);
                }
            });
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            commonMod.onServerStarted(server);
        });
        if(FabricLoader.getInstance().isModLoaded("fabric-data-generation-api-v1")) {
            RuntimeDatagen.init();
        }
    }
}
