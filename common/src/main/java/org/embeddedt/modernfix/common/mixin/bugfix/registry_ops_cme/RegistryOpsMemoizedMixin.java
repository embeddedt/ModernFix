package org.embeddedt.modernfix.common.mixin.bugfix.registry_ops_cme;

import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = {"net/minecraft/resources/RegistryOps$1"})
public class RegistryOpsMemoizedMixin {
    @Shadow @Final @Mutable
    private Map<ResourceKey<? extends Registry<?>>, Optional<? extends RegistryOps.RegistryInfo<?>>> lookups;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void useConcurrentMap(RegistryOps.RegistryInfoLookup registryInfoLookup, CallbackInfo ci) {
        this.lookups = new ConcurrentHashMap<>(this.lookups);
    }
}
