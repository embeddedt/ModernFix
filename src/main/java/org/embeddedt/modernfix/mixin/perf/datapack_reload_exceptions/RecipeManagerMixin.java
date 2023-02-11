package org.embeddedt.modernfix.mixin.perf.datapack_reload_exceptions;

import net.minecraft.item.crafting.RecipeManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {
    @Redirect(method = "apply(Ljava/util/Map;Lnet/minecraft/resources/IResourceManager;Lnet/minecraft/profiler/IProfiler;)V", at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"))
    private void silenceException(Logger instance, String s, Object location, Object exc) {
        instance.error(s + ": {}", location, exc.toString());
    }
}
