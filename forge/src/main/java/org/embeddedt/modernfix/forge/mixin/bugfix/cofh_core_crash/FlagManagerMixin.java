package org.embeddedt.modernfix.forge.mixin.bugfix.cofh_core_crash;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

/**
 * Fix getOrCreateFlag accessing the FLAGS map without synchronization by wrapping all calls to it
 * in a synchronized block.
 */
@Pseudo
@Mixin(targets = { "cofh/lib/util/flags/FlagManager" }, remap = false)
@RequiresMod("cofh_core")
public class FlagManagerMixin {
    @Shadow @Final
    private static Object2ObjectOpenHashMap<String, Supplier<Boolean>> FLAGS;

    @Shadow
    private Supplier<Boolean> getOrCreateFlag(String flag) {
        throw new AssertionError();
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "getOrCreateFlag"), require = 0)
    private Supplier<Boolean> getFlag(@Coerce Object flagHandler, String flag) {
        if(flagHandler != this)
            throw new AssertionError("Redirect targeted bad getOrCreateFlag invocation");
        synchronized (FLAGS) {
            return this.getOrCreateFlag(flag);
        }
    }
}
