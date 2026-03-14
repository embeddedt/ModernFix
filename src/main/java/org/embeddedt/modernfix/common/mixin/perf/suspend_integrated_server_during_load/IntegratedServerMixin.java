package org.embeddedt.modernfix.common.mixin.perf.suspend_integrated_server_during_load;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.suspend_integrated_server_during_load.IDeferrableIntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(IntegratedServer.class)
@ClientOnlyMixin
public abstract class IntegratedServerMixin implements IDeferrableIntegratedServer {
    private final AtomicBoolean mfix$hasPrimaryClientJoined = new AtomicBoolean(false);

    /**
     * @author embeddedt
     * @reason Wait to be finished processing all expensive packets (recipes, tags, etc.)
     * before continuing to tick the integrated server.
     */
    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isPaused()Z", ordinal = 0))
    private boolean preventTicks(Minecraft instance, Operation<Boolean> original) {
        return !mfix$hasPrimaryClientJoined.get() || original.call(instance);
    }

    @Override
    public void mfix$markClientLoadFinished() {
        mfix$hasPrimaryClientJoined.set(true);
    }
}
