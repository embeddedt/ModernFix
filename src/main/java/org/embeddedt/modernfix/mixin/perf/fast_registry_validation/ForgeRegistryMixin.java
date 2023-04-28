package org.embeddedt.modernfix.mixin.perf.fast_registry_validation;

import com.google.common.collect.BiMap;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.embeddedt.modernfix.registry.FastForgeRegistry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.*;

@Mixin(value = ForgeRegistry.class, remap = false)
public class ForgeRegistryMixin<V extends IForgeRegistryEntry<V>> {
    private static Method bitSetTrimMethod = null;
    private static boolean bitSetTrimMethodRetrieved = false;

    /**
     * Cache the result of findMethod instead of running it multiple times.
     * Null checks are not required as the surrounding code handles it already.
     */
    @Redirect(method = "validateContent", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/fml/common/ObfuscationReflectionHelper;findMethod(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"))
    private Method skipMultipleRemap(Class<?> clz, String methodName, Class<?>[] params) {
        if(!bitSetTrimMethodRetrieved) {
            bitSetTrimMethodRetrieved = true;
            bitSetTrimMethod = ObfuscationReflectionHelper.findMethod(clz, methodName, params);
        }
        return bitSetTrimMethod;
    }

    private int expectedNextBit = -1;

    /**
     * Avoid calling nextClearBit and scanning the whole registry for every block registration.
     */
    @Redirect(method = "add(ILnet/minecraftforge/registries/IForgeRegistryEntry;Ljava/lang/String;)I", at = @At(value = "INVOKE", target = "Ljava/util/BitSet;nextClearBit(I)I"))
    private int useCachedBit(BitSet availabilityMap, int minimum) {
        int bit = availabilityMap.nextClearBit(expectedNextBit != -1 ? expectedNextBit : minimum);
        expectedNextBit = bit + 1;
        return bit;
    }

    @Inject(method = { "sync", "clear", "block" }, at = @At("HEAD"))
    private void clearBitCache(CallbackInfo ci) {
        expectedNextBit = -1;
    }

    @Inject(method = "markDummy", at = @At(value = "INVOKE", target = "Ljava/util/BitSet;clear(I)V"))
    private void clearBitCache2(CallbackInfoReturnable<Boolean> cir) {
        expectedNextBit = -1;
    }

    /*
    @Redirect(method = "add(ILnet/minecraftforge/registries/IForgeRegistryEntry;Ljava/lang/String;)I", at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;trace(Lorg/apache/logging/log4j/Marker;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V"))
    private void skipTrace(Logger logger, Marker marker, String s, Object o, Object o1, Object o2, Object o3, Object o4) {

    }

     */

    @Shadow @Final @Mutable private BiMap<Integer, V> ids;

    @Shadow @Final @Mutable private BiMap<ResourceKey<V>, V> keys;

    @Shadow @Final private ResourceKey<Registry<V>> key;

    @Shadow @Final @Mutable private BiMap<ResourceLocation, V> names;

    /**
     * The following code replaces the Forge HashBiMaps with a more efficient data structure based around
     * an array list for IDs and one HashMap going from value -> information.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceBackingMaps(CallbackInfo ci) {
        FastForgeRegistry<V> fastReg = new FastForgeRegistry<>(this.key);
        this.ids = fastReg.getIds();
        this.keys = fastReg.getKeys();
        this.names = fastReg.getNames();
    }
}
