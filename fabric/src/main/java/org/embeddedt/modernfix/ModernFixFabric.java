package org.embeddedt.modernfix;

import net.fabricmc.api.ModInitializer;
import net.minecraft.server.MinecraftServer;

import java.lang.ref.WeakReference;

public class ModernFixFabric implements ModInitializer {
    public static ModernFix commonMod;
    public static WeakReference<MinecraftServer> theServer = new WeakReference<>(null);
    @Override
    public void onInitialize() {
        commonMod = new ModernFix();

        // TODO: implement entity ID desync
    }


}
