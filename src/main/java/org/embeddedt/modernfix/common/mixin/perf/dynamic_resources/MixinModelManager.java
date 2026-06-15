package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.client.resources.model.ModelDiscovery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.cuboid.ItemModelGenerator;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelLoader;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynresources.BlockStateModelMap;
import org.embeddedt.modernfix.dynresources.DynamicModelSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
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
     * @reason Stop all models from being loaded at startup by the model resolution logic.
     */
    @Redirect(
            method = "discoverModelDependencies(Ljava/util/Map;Lnet/minecraft/client/resources/model/BlockStateModelLoader$LoadedModels;Lnet/minecraft/client/resources/model/ClientItemInfoLoader$LoadedClientInfos;Lnet/neoforged/neoforge/client/model/standalone/StandaloneModelLoader$LoadedModels;)Lnet/minecraft/client/resources/model/ModelManager$ResolvedModels;",
            at = @At(value = "INVOKE", target = "Ljava/util/Collection;forEach(Ljava/util/function/Consumer;)V"))
    private static <T> void skipAddingRoot(Collection<T> instance, Consumer<T> consumer) {
    }

    /**
     * @author embeddedt, coredex
     * @reason Divert to our dynamic resolver. It is cleaner to overwrite the whole method, but this seems to cause
     * a conflict with Sodium.
     */
    @Redirect(
            method = "discoverModelDependencies(Ljava/util/Map;Lnet/minecraft/client/resources/model/BlockStateModelLoader$LoadedModels;Lnet/minecraft/client/resources/model/ClientItemInfoLoader$LoadedClientInfos;Lnet/neoforged/neoforge/client/model/standalone/StandaloneModelLoader$LoadedModels;)Lnet/minecraft/client/resources/model/ModelManager$ResolvedModels;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelDiscovery;resolve()Ljava/util/Map;")
    )
    private static Map<Identifier, ResolvedModel> useDynamicResolverMap(
            ModelDiscovery discovery,
            Map<Identifier, UnbakedModel> allModels,
            BlockStateModelLoader.LoadedModels blockStateModels,
            ClientItemInfoLoader.LoadedClientInfos itemInfos,
            StandaloneModelLoader.LoadedModels standaloneModels
    ) {
        UnbakedModel generatedItemModel;
        var generatedItemWrapper = ((ModelDiscoveryAccessor) discovery).mfix$getModelWrappers().get(ItemModelGenerator.GENERATED_ITEM_MODEL_ID);
        if (generatedItemWrapper != null) {
            generatedItemModel = generatedItemWrapper.wrapped();
        } else {
            generatedItemModel = new ItemModelGenerator();
        }
        return new DynamicModelSystem.DynamicResolver(allModels, blockStateModels, itemInfos, standaloneModels, generatedItemModel).resolvedModelsMap();
    }

    /**
     * @author embeddedt
     * @reason Build the model groups dynamically
     */
    @Overwrite
    private static Object2IntMap<BlockState> buildModelGroups(BlockColors blockColors, BlockStateModelLoader.LoadedModels loadedModels) {
        return new DynamicModelSystem.BlockGroupingMap(blockColors, loadedModels);
    }

    /**
     * @author embeddedt
     * @reason avoid copying inner map
     */
    @Overwrite
    private static Map<BlockState, BlockStateModel> createBlockStateToModelDispatch(Map<BlockState, BlockStateModel> blockStateModels, BlockStateModel missingModel) {
        BlockStateModelMap.resetCache();
        Objects.requireNonNull(missingModel);
        return new BlockStateModelMap(blockStateModels, missingModel);
    }
}
