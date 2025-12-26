package org.embeddedt.modernfix.forge.packet;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;

public class PacketHandler {
    public static final SimpleChannel INSTANCE = buildChannel("main", "1");
    public static final SimpleChannel INGREDIENT_SYNC;

    static {
        SimpleChannel ingredientChannel;
        if (ModernFixMixinPlugin.instance.isOptionEnabled("perf.smart_ingredient_sync.Channel")) {
            ingredientChannel = buildChannel("ingredient_sync", "1");
        } else {
            ingredientChannel = null;
        }
        INGREDIENT_SYNC = ingredientChannel;
    }

    public static final ThreadLocal<Boolean> CLIENT_HAS_SMART_INGREDIENT_SYNC = ThreadLocal.withInitial(() -> false);

    private static SimpleChannel buildChannel(String name, String version) {
        return NetworkRegistry.newSimpleChannel(
                new ResourceLocation(ModernFix.MODID, name),
                () -> version,
                NetworkRegistry.acceptMissingOr(version),
                NetworkRegistry.acceptMissingOr(version)
        );
    }

    public static void register() {
    }
}
