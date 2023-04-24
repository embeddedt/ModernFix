package org.embeddedt.modernfix.mixin.perf.dedicated_reload_executor;

import net.minecraft.client.Minecraft;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.Executor;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Redirect(method = { "<init>", "makeServerStem", "reloadResourcePacks" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;backgroundExecutor()Ljava/util/concurrent/Executor;", ordinal = 0))
    private Executor getResourceReloadExecutor() {
        return ModernFix.resourceReloadExecutor();
    }
}
