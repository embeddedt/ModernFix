package org.embeddedt.modernfix.forge.mixin.bugfix.cofh_core_crash;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Fix getOrCreateFlag accessing the FLAGS map without synchronization by wrapping all calls to it
 * in a synchronized block.
 */
@Pseudo
@Mixin(targets = { "cofh/lib/util/flags/FlagManager" }, remap = false)
@RequiresMod("cofh_core")
public class FlagManagerMixin {
    @Shadow @Final
    private static Object2ObjectOpenHashMap<String, ?> FLAGS;

    @Unique
    private static final MethodHandle mfix$getOrCreateFlag;

    static {
        // use this reflection dance to avoid depending on whether it's implemented via BooleanSupplier or Supplier<Boolean>
        try {
            Method m = MethodHandles.lookup().lookupClass().getDeclaredMethod("getOrCreateFlag", String.class);
            m.setAccessible(true);
            mfix$getOrCreateFlag = MethodHandles.lookup().unreflect(m);
        } catch(ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "getOrCreateFlag"), require = 0)
    @Coerce
    private Object getFlag(@Coerce Object flagHandler, String flag) {
        if(flagHandler != this)
            throw new AssertionError("Redirect targeted bad getOrCreateFlag invocation");
        synchronized (FLAGS) {
            try {
                return mfix$getOrCreateFlag.invoke((Object)this, flag);
            } catch(Throwable e) {
                if(e instanceof RuntimeException)
                    throw (RuntimeException)e;
                else
                    throw new RuntimeException(e);
            }
        }
    }
}
