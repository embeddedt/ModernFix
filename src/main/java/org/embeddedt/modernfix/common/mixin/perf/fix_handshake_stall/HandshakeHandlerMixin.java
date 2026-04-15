package org.embeddedt.modernfix.common.mixin.perf.fix_handshake_stall;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraftforge.network.HandshakeHandler;
import net.minecraftforge.network.NetworkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;

@Mixin(value = HandshakeHandler.class, remap = false)
public class HandshakeHandlerMixin {
    @Shadow
    private int packetPosition;

    @Shadow
    private List<NetworkRegistry.LoginPayload> messageList;

    @Shadow
    private List<Integer> sentMessages;

    /**
     * @author embeddedt
     * @reason we must synchronize sentMessages because it is modified from both the Netty thread and the
     * server thread
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void synchronizeSentMessages(CallbackInfo ci) {
        this.sentMessages = Collections.synchronizedList(this.sentMessages);
    }

    /**
     * @author embeddedt
     * @reason Forge only sends one login payload per tick. It takes many seconds to send all the payloads at this rate.
     * During this time, the game remains frozen on the chunk loading screen with almost zero CPU usage.
     * To fix this, we re-tick the handshake handler until the packetPosition stops advancing or the handler indicates
     * it no longer needs ticking.
     */
    @WrapMethod(method = "tickServer")
    private boolean modernfix$retick(Operation<Boolean> original) {
        boolean isDoneTicking;
        int prevPacketPosition;
        do {
            prevPacketPosition = this.packetPosition;
            isDoneTicking = original.call();
        } while(!isDoneTicking && this.packetPosition > prevPacketPosition);
        return isDoneTicking;
    }

    /**
     * @author embeddedt
     * @reason The original HandshakeHandler has an off-by-one error in its completion check. We patch this to prevent
     * our optimization from potentially triggering it more often due to the timing change.
     */
    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z", ordinal = 0), slice = @Slice(from = @At(value = "INVOKE", target = "Ljava/util/List;removeIf(Ljava/util/function/Predicate;)Z", ordinal = 0)))
    private boolean preventEarlyExit(List<?> instance, Operation<Boolean> original) {
        if (instance != this.sentMessages) {
            throw new AssertionError("Injector is misplaced");
        }
        return original.call(instance) && this.packetPosition >= this.messageList.size();
    }
}
