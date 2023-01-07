package org.embeddedt.modernfix.mixin.perf.parallel_potentially_unsafe.parallel_deferred_suppliers;

import com.sun.org.apache.xpath.internal.operations.Bool;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import org.embeddedt.modernfix.registry.DeferredRegisterBaker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;

@Mixin(DeferredRegister.EventDispatcher.class)
public class EventDispatcherMixin {
    private static HashMap<ResourceLocation, Boolean> hasRegistryBaked = new HashMap<>();
    @Inject(method = "handleEvent", at = @At("HEAD"), remap = false)
    private void bakeIfNeeded(RegistryEvent.Register<?> event, CallbackInfo ci) {
        IForgeRegistry<?> registry = event.getRegistry();
        if(registry == null)
            return;
        ResourceLocation location = registry.getRegistryName();
        if(location == null)
            return;
        if(!hasRegistryBaked.getOrDefault(location, false)) {
            DeferredRegisterBaker.bakeSuppliers(location);
            hasRegistryBaked.put(location, true);
        }
    }
}
