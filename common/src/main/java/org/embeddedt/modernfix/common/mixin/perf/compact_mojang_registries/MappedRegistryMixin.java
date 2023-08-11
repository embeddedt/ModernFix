package org.embeddedt.modernfix.common.mixin.perf.compact_mojang_registries;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
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
public abstract class MappedRegistryMixin<T> extends Registry<T> {
    @Shadow
    @Final
    @Mutable
    private Map<T, Lifecycle> lifecycles;

    private static final ImmutableSet<ResourceLocation> MFIX$NEW_STORAGE_KEYS = ImmutableSet.of(new ResourceLocation("block"), new ResourceLocation("item"));

    protected MappedRegistryMixin(ResourceKey<? extends Registry<T>> resourceKey, Lifecycle lifecycle) {
        super(resourceKey, lifecycle);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceStorage(CallbackInfo ci) {
        this.lifecycles = new LifecycleMap<>();
        /*
        if(MFIX$NEW_STORAGE_KEYS.contains(this.key().location())) {
            ModernFixMixinPlugin.instance.logger.info("Using experimental registry storage for {}", this.key());
            this.storage = (BiMap<ResourceLocation, T>) RegistryStorage.createStorage();
            this.keyStorage = (BiMap<ResourceKey<T>, T>)RegistryStorage.createKeyStorage(this.key(), (BiMap<ResourceLocation, DirectStorageRegistryObject>)this.storage);
        }
        */
    }
}
