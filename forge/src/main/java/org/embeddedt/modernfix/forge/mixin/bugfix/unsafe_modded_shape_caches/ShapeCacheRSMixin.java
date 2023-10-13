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
 * Some mods use a custom shape cache with a non-thread-safe map. There is no reason why this wouldn't cause crashes
 * in vanilla as well if getShape was called on two threads at once. It seems more likely to happen with ModernFix
 * installed due to the dynamic blockstate cache generation, so we solve it by making the maps thread-safe.
 */
@Pseudo
@Mixin(targets = { "com/refinedmods/refinedstorage/block/shape/ShapeCache" }, remap = false)
@RequiresMod("refinedstorage")
@SuppressWarnings("rawtypes")
public class ShapeCacheRSMixin {
    @Shadow @Final @Mutable private static final Map CACHE = new ConcurrentHashMap();

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void mfix$notify(CallbackInfo ci) {
        ModernFix.LOGGER.info("Made Refined Storage shape cache map thread-safe");
    }
}
