package org.embeddedt.modernfix.forge.mixin.bugfix.unsafe_modded_shape_caches;

import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * see {@link ShapeCacheRSMixin}
 */
@Pseudo
@Mixin(targets = { "com/lothrazar/cyclic/block/cable/ShapeCache" }, remap = false)
@RequiresMod("cyclic")
@SuppressWarnings("rawtypes")
public class ShapeCacheCyclicMixin {
    @Shadow @Final @Mutable private static final Map CACHE = new ConcurrentHashMap();

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void mfix$notify(CallbackInfo ci) {
        ModernFix.LOGGER.info("Made Cyclic shape cache map thread-safe");
    }
}
