package org.embeddedt.modernfix.mixin.perf.fast_registry_validation;

import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;

@Mixin(value = ForgeRegistry.class, remap = false)
public class ForgeRegistryMixin {
    private static Method bitSetTrimMethod = null;
    private static boolean bitSetTrimMethodRetrieved = false;

    /**
     * Cache the result of findMethod instead of running it multiple times.
     * Null checks are not required as the surrounding code handles it already.
     */
    @Redirect(method = "validateContent", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/util/ObfuscationReflectionHelper;findMethod(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"))
    private Method skipMultipleRemap(Class<?> clz, String methodName, Class<?>[] params) {
        if(!bitSetTrimMethodRetrieved) {
            bitSetTrimMethodRetrieved = true;
            bitSetTrimMethod = ObfuscationReflectionHelper.findMethod(clz, methodName, params);
        }
        return bitSetTrimMethod;
    }
}
