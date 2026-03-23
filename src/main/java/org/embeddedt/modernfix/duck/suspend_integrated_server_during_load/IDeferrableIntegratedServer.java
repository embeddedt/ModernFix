package org.embeddedt.modernfix.duck.suspend_integrated_server_during_load;

import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.ModernFix;

public interface IDeferrableIntegratedServer {
    ResourceLocation CLIENT_LOAD_SENTINEL = ResourceLocation.fromNamespaceAndPath(ModernFix.MODID, "mark_client_load_finished");

    void mfix$markClientLoadFinished();
}
