package org.embeddedt.modernfix.common.mixin.perf.compact_mojang_registries;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(VanillaRegistries.class)
public class VanillaRegistriesMixin {
    private static HolderLookup.Provider STATIC_PROVIDER;

    @WrapMethod(method = "createLookup")
    private static HolderLookup.Provider modernfix$memoizeLookup(Operation<HolderLookup.Provider> original) {
        synchronized (VanillaRegistries.class) {
            if (STATIC_PROVIDER == null) {
                STATIC_PROVIDER = original.call();
            }
            return STATIC_PROVIDER;
        }
    }
}
