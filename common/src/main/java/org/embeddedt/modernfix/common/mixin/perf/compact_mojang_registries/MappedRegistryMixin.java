package org.embeddedt.modernfix.common.mixin.perf.compact_mojang_registries;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.annotation.IgnoreOutsideDev;
import org.embeddedt.modernfix.registry.LifecycleMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(MappedRegistry.class)
@IgnoreOutsideDev
public abstract class MappedRegistryMixin<T> {
    @Shadow
    @Final
    @Mutable
    private Map<ResourceKey<T>, RegistrationInfo> registrationInfos;

    private static final ImmutableSet<ResourceLocation> MFIX$NEW_STORAGE_KEYS = ImmutableSet.of(new ResourceLocation("block"), new ResourceLocation("item"));

    @Inject(method = "<init>(Lnet/minecraft/resources/ResourceKey;Lcom/mojang/serialization/Lifecycle;Z)V", at = @At("RETURN"))
    private void replaceStorage(CallbackInfo ci) {
        this.registrationInfos = new LifecycleMap<>();
        /*
        if(MFIX$NEW_STORAGE_KEYS.contains(this.key().location())) {
            ModernFixMixinPlugin.instance.logger.info("Using experimental registry storage for {}", this.key());
            this.storage = (BiMap<ResourceLocation, T>) RegistryStorage.createStorage();
            this.keyStorage = (BiMap<ResourceKey<T>, T>)RegistryStorage.createKeyStorage(this.key(), (BiMap<ResourceLocation, DirectStorageRegistryObject>)this.storage);
        }
        */
    }
}
