package org.embeddedt.modernfix.common.mixin.perf.suspend_integrated_server_during_load;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.suspend_integrated_server_during_load.IDeferrableIntegratedServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
@ClientOnlyMixin
public class ClientPacketListenerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void detectClientLoadSentinel(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (packet.getIdentifier().equals(IDeferrableIntegratedServer.CLIENT_LOAD_SENTINEL)) {
            // Important: flag must be changed on the client thread, as later packets can start decoding
            // while earlier ones are still being applied.
            this.minecraft.executeIfPossible(() -> {
                packet.getData().release();
                if (this.minecraft.hasSingleplayerServer()) {
                    ((IDeferrableIntegratedServer)this.minecraft.getSingleplayerServer()).mfix$markClientLoadFinished();
                }
            });
            ci.cancel();
        }
    }
}
