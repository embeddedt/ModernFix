package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import net.minecraft.client.resources.model.ModelBakery;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynresources.DynamicModelSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

@Mixin(ModelBakery.class)
@ClientOnlyMixin
public class MixinModelBakery {
    /**
     * @author embeddedt
     * @reason Create dynamic baked registry with cache instead of baking all entries from the input map at once
     */
    @Redirect(method = "bakeModels", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/thread/ParallelMapTransform;schedule(Ljava/util/Map;Ljava/util/function/BiFunction;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private <K, U, V> CompletableFuture<Map<K, V>> dynamicallyBake(Map<K, U> input, BiFunction<K, U, V> baker, Executor executor) {
        return CompletableFuture.completedFuture(DynamicModelSystem.createDynamicBakedRegistry(input, baker));
    }
}
