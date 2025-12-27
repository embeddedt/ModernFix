package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynresources.DynamicModelSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@Mixin(ModelManager.class)
@ClientOnlyMixin
public class MixinModelManager {
    /**
     * @author embeddedt
     * @reason Instead of loading all unbaked models from the resource packs at once, create a dynamic map backed by
     * a cache that loads them on demand
     */
    @ModifyArg(method = "loadBlockModels", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenCompose(Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;"))
    private static Function<Map<Identifier, Resource>, ? extends CompletionStage<Map<Identifier, UnbakedModel>>> skipAOTUnbakedModelLoad(Function<Map<Identifier, Resource>, ? extends CompletionStage<Map<Identifier, UnbakedModel>>> original) {
        return resourceMap -> CompletableFuture.completedFuture(DynamicModelSystem.createDynamicUnbakedModelMap(resourceMap));
    }

    /**
     * @author embeddedt
     * @reason Model resolution is not necessary, because we dynamically find models as they are referenced
     */
    @Overwrite
    private static ModelManager.ResolvedModels discoverModelDependencies(
            Map<Identifier, UnbakedModel> inputModels, BlockStateModelLoader.LoadedModels loadedModels, ClientItemInfoLoader.LoadedClientInfos loadedClientInfos, net.neoforged.neoforge.client.model.standalone.StandaloneModelLoader.LoadedModels standaloneModels
    ) {
        return new DynamicModelSystem.DynamicResolver(inputModels, loadedModels, loadedClientInfos, standaloneModels).resolvedModels();
    }
}
