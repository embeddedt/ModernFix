package org.embeddedt.modernfix.duck.suspend_integrated_server_during_load;

import net.minecraft.resources.Identifier;
import org.embeddedt.modernfix.ModernFix;

public interface IDeferrableIntegratedServer {
    Identifier CLIENT_LOAD_SENTINEL = Identifier.fromNamespaceAndPath(ModernFix.MODID, "mark_client_load_finished");

    void mfix$markClientLoadFinished();
}
