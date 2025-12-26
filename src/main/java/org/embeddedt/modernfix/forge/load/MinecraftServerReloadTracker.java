package org.embeddedt.modernfix.forge.load;

public class MinecraftServerReloadTracker {
    public static int ACTIVE_RELOADS = 0;

    public static boolean isReloadActive() {
        return ACTIVE_RELOADS > 0;
    }
}
