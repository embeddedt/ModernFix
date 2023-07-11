package org.embeddedt.modernfix.forge.mixin.bugfix.removed_dimensions;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.RegistryLoader;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RegistryLoader.class)
public class RegistryLoaderMixin {
    @Inject(method = "overrideElementFromResources", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/WritableRegistry;getOrCreateHolder(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder;", ordinal = 0), cancellable = true)
    private <E> void handleErroringHolder(WritableRegistry<E> arg, ResourceKey<? extends Registry<E>> arg2, Codec<E> codec, ResourceKey<E> arg3, DynamicOps<JsonElement> dynamicOps, CallbackInfoReturnable<DataResult<Holder<E>>> cir) {
        try {
            arg.getOrCreateHolder(arg3);
        } catch(RuntimeException e) {
            cir.setReturnValue(DataResult.error("Missing holder for " + arg3));
        }
    }
}
