package org.embeddedt.modernfix;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.embeddedt.modernfix.fabric.datagen.RuntimeDatagen;

public class ModernFixClientFabric implements ClientModInitializer {
    public static ModernFixClient commonMod;

    @Override
    public void onInitializeClient() {
        commonMod = new ModernFixClient();

        org.embeddedt.modernfix.fabric.modfixs.DecorativeBlocksFix.fix();

        if(FabricLoader.getInstance().isModLoaded("fabric-data-generation-api-v1")) {
            RuntimeDatagen.init();
        }
    }
}
