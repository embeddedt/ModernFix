package org.embeddedt.modernfix.platform;

import java.lang.reflect.Constructor;

class PlatformHookLoader {
    static ModernFixPlatformHooks findInstance() {
        String[] locations = new String[] { "forge", "fabric" };
        for(String location : locations) {
            try {
                Class<?> clz = Class.forName("org.embeddedt.modernfix.platform." + location + ".ModernFixPlatformHooksImpl");
                Constructor<?> constructor = clz.getConstructor();
                constructor.setAccessible(true);
                return (ModernFixPlatformHooks)constructor.newInstance();
            } catch(ClassNotFoundException ignored) {
            } catch(ReflectiveOperationException | ClassCastException e) {
                e.printStackTrace();
            }
        }
        System.err.println("ModernFix has failed to load platform hooks. It cannot function, the game will now close");
        Runtime.getRuntime().exit(1);
        throw new AssertionError("Somehow couldn't exit");
    }
}
