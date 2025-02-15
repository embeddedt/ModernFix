package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.google.common.collect.ForwardingMap;
import com.google.common.collect.Maps;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.AtlasSet;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.ArrayUtils;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.IModelHoldingBlockState;
import org.embeddedt.modernfix.dynamicresources.DynamicModelProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ModelManager.class)
@ClientOnlyMixin
public class ModelManagerMixin implements DynamicModelProvider.ModelManagerExtension {
    @Shadow private Map<ResourceLocation, ItemModel> bakedItemStackModels;
    @Shadow private Map<ResourceLocation, ClientItem.Properties> itemProperties;

    @Unique
    private DynamicModelProvider mfix$modelProvider;

    @Redirect(method = "reload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelManager;loadBlockModels(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Map<ResourceLocation, BlockModel>> deferBlockModelLoad(ResourceManager manager, Executor executor) {
        return CompletableFuture.completedFuture(Map.of());
    }

    @Redirect(method = "reload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/BlockStateModelLoader;loadBlockStates(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<BlockStateModelLoader.LoadedModels> deferBlockStateLoad(ResourceManager resourceManager, Executor executor) {
        return CompletableFuture.completedFuture(new BlockStateModelLoader.LoadedModels(Map.of()));
    }

    /**
     * @author embeddedt
     * @reason disable map creation, use dynamic dispatch
     */
    @Overwrite
    private static Map<BlockState, BlockStateModel> createBlockStateToModelDispatch(Map<BlockState, BlockStateModel> map, BlockStateModel missingModel) {
        var dynamicProvider = Objects.requireNonNull(DynamicModelProvider.currentReloadingModelProvider.get());

        var dynamicRegistry = dynamicProvider.getTopLevelEmulatedRegistry();

        return new ForwardingMap<>() {
            @Override
            protected Map<BlockState, BlockStateModel> delegate() {
                return dynamicRegistry;
            }

            @Override
            public BlockStateModel get(Object key) {
                BlockStateModel result;
                if (key instanceof IModelHoldingBlockState state) {
                    result = state.mfix$getModel();
                    if (result != null) {
                        return result;
                    }
                }
                result = dynamicRegistry.getOrDefault(key, dynamicProvider.getMissingBakedModel());
                if (key instanceof IModelHoldingBlockState state) {
                    state.mfix$setModel(result);
                }
                return result;
            }
        };
    }

    @Redirect(method = "reload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ClientItemInfoLoader;scheduleLoad(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> disableClientItemEarlyLoad(ResourceManager resourceManager, Executor executor) {
        return CompletableFuture.completedFuture(new ClientItemInfoLoader.LoadedClientInfos(Map.of()));
    }

    @ModifyArg(method = "reload", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;allOf([Ljava/util/concurrent/CompletableFuture;)Ljava/util/concurrent/CompletableFuture;", ordinal = 1))
    private CompletableFuture<?>[] createModelProvider(CompletableFuture<?>[] cfs, @Local(ordinal = 0) CompletableFuture<EntityModelSet> entityModelFuture, @Local(ordinal = 0, argsOnly = true) Executor executor, @Local(ordinal = 0) Map<ResourceLocation, CompletableFuture<AtlasSet.StitchResult>> atlasPreparations) {
        CompletableFuture<Void> makeModelProviderFuture = CompletableFuture.supplyAsync(() -> {
            return Map.copyOf(Maps.transformValues(atlasPreparations, CompletableFuture::join));
        }, executor).thenAcceptBoth(entityModelFuture, (stitchResults, entityModelSet) -> {
            this.mfix$modelProvider = new DynamicModelProvider(
                    Minecraft.getInstance().getResourceManager(),
                    entityModelSet,
                    stitchResults
            );
            DynamicModelProvider.currentReloadingModelProvider = new WeakReference<>(this.mfix$modelProvider);
        });
        return ArrayUtils.add(cfs, makeModelProviderFuture);
    }

    @Inject(method = "apply", at = @At("RETURN"))
    private void setModelRegistries(CallbackInfo ci) {
        this.bakedItemStackModels = this.mfix$modelProvider.getItemModelEmulatedRegistry();
        this.itemProperties = this.mfix$modelProvider.getItemPropertiesEmulatedRegistry();
        for(Block block : BuiltInRegistries.BLOCK) {
            for(BlockState state : block.getStateDefinition().getPossibleStates()) {
                if(state instanceof IModelHoldingBlockState modelHolder) {
                    modelHolder.mfix$setModel(null);
                }
            }
        }
    }

    @Override
    public DynamicModelProvider mfix$getModelProvider() {
        return this.mfix$modelProvider;
    }
}
