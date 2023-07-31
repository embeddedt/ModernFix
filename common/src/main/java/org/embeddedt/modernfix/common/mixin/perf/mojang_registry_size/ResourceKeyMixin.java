package org.embeddedt.modernfix.common.mixin.perf.mojang_registry_size;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(ResourceKey.class)
public class ResourceKeyMixin<T> {
    private static final Map<ResourceLocation, Map<ResourceLocation, ResourceKey<?>>> INTERNING_MAP = new Object2ObjectOpenHashMap<>();
    @Inject(method = "create(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/resources/ResourceKey;", at = @At("HEAD"), cancellable = true)
    private static <T> void createEfficient(ResourceLocation parent, ResourceLocation location, CallbackInfoReturnable<ResourceKey<T>> cir) {
        synchronized (ResourceKey.class) {
            Map<ResourceLocation, ResourceKey<?>> keys = INTERNING_MAP.computeIfAbsent(parent, k -> new Object2ObjectOpenHashMap<>());
            ResourceKey<?> key = keys.get(location);
            if(key == null) {
                key = new ResourceKey<>(parent, location);
                keys.put(location, key);
            }
            cir.setReturnValue((ResourceKey<T>)key);
        }
    }
}
