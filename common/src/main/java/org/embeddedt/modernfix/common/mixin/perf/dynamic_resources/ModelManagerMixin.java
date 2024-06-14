package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.AtlasSet;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.embeddedt.modernfix.duck.IExtendedModelManager;
import org.embeddedt.modernfix.util.LambdaMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Mixin(ModelManager.class)
@ClientOnlyMixin
public class ModelManagerMixin implements IExtendedModelManager {
    @Shadow private Map<ResourceLocation, BakedModel> bakedRegistry;

    @Unique
    private Runnable tickHandler = () -> {};

    @Inject(method = "<init>", at = @At("RETURN"))
    private void injectDummyBakedRegistry(CallbackInfo ci) {
        if(this.bakedRegistry == null) {
            this.bakedRegistry = new HashMap<>();
        }
    }

    @Redirect(method = "reload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelManager;loadBlockModels(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Map<ResourceLocation, BlockModel>> deferBlockModelLoad(ResourceManager manager, Executor executor) {
        return CompletableFuture.completedFuture(new LambdaMap<>(location -> loadSingleBlockModel(manager, location)));
    }

    @Redirect(method = "reload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelManager;loadBlockStates(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>>> deferBlockStateLoad(ResourceManager manager, Executor executor) {
        return CompletableFuture.completedFuture(new LambdaMap<>(location -> loadSingleBlockState(manager, location)));
    }

    @Redirect(method = "loadModels", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/StateDefinition;getPossibleStates()Lcom/google/common/collect/ImmutableList;"))
    private ImmutableList<BlockState> skipCollection(StateDefinition<Block, BlockState> definition) {
        return ImmutableList.of();
    }

    private BlockModel loadSingleBlockModel(ResourceManager manager, ResourceLocation location) {
        return manager.getResource(location).map(resource -> {
            try (BufferedReader reader = resource.openAsReader()) {
                return BlockModel.fromStream(reader);
            } catch(IOException e) {
                ModernFix.LOGGER.error("Couldn't load model", e);
                return null;
            }
        }).orElse(null);
    }

    private List<BlockStateModelLoader.LoadedJson> loadSingleBlockState(ResourceManager manager, ResourceLocation location) {
        return manager.getResourceStack(location).stream().map(resource -> {
            try (BufferedReader reader = resource.openAsReader()) {
                return new BlockStateModelLoader.LoadedJson(resource.sourcePackId(), GsonHelper.parse(reader));
            } catch(IOException e) {
                ModernFix.LOGGER.error("Couldn't load blockstate", e);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Inject(method = "loadModels", at = @At("RETURN"))
    private void storeTicker(ProfilerFiller profilerFiller, Map<ResourceLocation, AtlasSet.StitchResult> map, ModelBakery modelBakery, CallbackInfoReturnable<?> cir) {
        tickHandler = ((IExtendedModelBakery)modelBakery)::mfix$tick;
    }

    @Inject(method = "apply", at = @At("RETURN"))
    private void freezeBakery(@Coerce Object reloadState, ProfilerFiller profilerFiller, CallbackInfo ci, @Local(ordinal = 0) ModelBakery bakery) {
        ((IExtendedModelBakery)bakery).mfix$finishLoading();
    }

    @Override
    public void mfix$tick() {
        tickHandler.run();
    }
}
