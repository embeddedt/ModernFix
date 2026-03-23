package org.embeddedt.modernfix.common.mixin.perf.suspend_integrated_server_during_load;

import net.minecraft.client.Minecraft;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
@ClientOnlyMixin
public class MinecraftMixin {
    /**
     * @author embeddedt
     * @reason spin-waiting burns CPU time on the main thread, when the server thread is likely to take some time
     * to be ready.
     */
    @Redirect(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;yield()V"))
    private void sleepInsteadOfYield() {
        try {
            Thread.sleep(16L);
        } catch (InterruptedException ignored) {
        }
    }
}
