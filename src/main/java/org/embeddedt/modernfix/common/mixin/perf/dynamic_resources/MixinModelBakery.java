package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.google.common.collect.Maps;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.Identifier;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynresources.DynamicModelSystem;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
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

    @Redirect(method = "bakeModels",
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/client/resources/model/ModelBakery;clientInfos:Ljava/util/Map;", opcode = Opcodes.GETFIELD, ordinal = 2)),
            at = @At(value = "INVOKE", target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V", ordinal = 0))
    private void dynamicItemProperties(Map<Identifier, ClientItem> clientItems, BiConsumer<? super Identifier, ? super ClientItem> action,
                               @Local(name = "itemStackModelProperties") LocalRef<Map<Identifier, ClientItem.Properties>> modelProperties) {
        modelProperties.set(Maps.asMap(clientItems.keySet(), id -> {
            var item = clientItems.get(id);
            var props = ClientItem.Properties.DEFAULT;
            if (item != null && !props.equals(item.properties())) {
                props = item.properties();
            }
            return props;
        }));
    }

    /**
     * @author embeddedt
     * @reason We want log4j to print the stacktrace and not just the exception message
     */
    @ModifyConstant(method = "lambda$bakeModels$0", constant = @Constant(stringValue = "Unable to bake model: '{}': {}"))
    private static String showFullException(String prefix) {
        return "Unable to bake model: '{}'";
    }
}
