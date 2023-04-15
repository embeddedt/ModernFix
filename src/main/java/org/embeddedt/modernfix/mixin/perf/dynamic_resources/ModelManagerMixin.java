package org.embeddedt.modernfix.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ModelManager.class)
public class ModelManagerMixin {
    @Inject(method = "loadBlockModels", at = @At("HEAD"), cancellable = true)
    private static void deferBlockModelLoad(ResourceManager manager, Executor executor, CallbackInfoReturnable<CompletableFuture<Map<ResourceLocation, BlockModel>>> cir) {
        cir.setReturnValue(CompletableFuture.completedFuture(new HashMap<>()));
    }

    @Inject(method = "loadBlockStates", at = @At("HEAD"), cancellable = true)
    private static void deferBlockStateLoad(ResourceManager manager, Executor executor, CallbackInfoReturnable<CompletableFuture<Map<ResourceLocation, List<ModelBakery.LoadedJson>>>> cir) {
        cir.setReturnValue(CompletableFuture.completedFuture(new HashMap<>()));
    }
}
