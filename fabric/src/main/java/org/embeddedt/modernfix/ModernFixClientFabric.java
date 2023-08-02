package org.embeddedt.modernfix;

import net.fabricmc.api.ClientModInitializer;

public class ModernFixClientFabric implements ClientModInitializer {
    public static ModernFixClient commonMod;

    @Override
    public void onInitializeClient() {
        commonMod = new ModernFixClient();
    }
}
