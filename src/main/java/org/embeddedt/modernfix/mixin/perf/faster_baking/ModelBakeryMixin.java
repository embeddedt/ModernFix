package org.embeddedt.modernfix.mixin.perf.faster_baking;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.model.*;
import net.minecraft.client.renderer.model.multipart.Multipart;
import net.minecraft.client.renderer.model.multipart.Selector;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.texture.SpriteMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.profiler.IProfiler;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.vector.TransformationMatrix;
import net.minecraftforge.fml.loading.progress.StartupMessageManager;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.embeddedt.modernfix.ModernFix.LOGGER;

@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin {
    @Shadow @Final private Map<ResourceLocation, IUnbakedModel> topLevelModels;

    @Shadow @Final private Map<ResourceLocation, IBakedModel> bakedTopLevelModels;

    @Shadow @Deprecated @Nullable public abstract IBakedModel bake(ResourceLocation pLocation, IModelTransform pTransform);

    @Shadow private Map<ResourceLocation, Pair<AtlasTexture, AtlasTexture.SheetData>> atlasPreparations;

    @Shadow @Nullable private SpriteMap atlasSet;

    @Shadow @Final private Map<ResourceLocation, IUnbakedModel> unbakedCache;
    @Shadow @Mutable
    @Final private Map<Triple<ResourceLocation, TransformationMatrix, Boolean>, IBakedModel> bakedCache;

    @Shadow @Final private static ItemModelGenerator ITEM_MODEL_GENERATOR;
    @Shadow @Final public static BlockModel GENERATION_MARKER;

    @Shadow public abstract IUnbakedModel getModel(ResourceLocation p_209597_1_);

    private ConcurrentHashMap<ResourceLocation, IdentityHashMap<IModelTransform, IBakedModel>> fasterBakedCache;

    private Map<Boolean, List<Map.Entry<ResourceLocation, IUnbakedModel>>> modelsToBakeParallel;

    private boolean canBakeParallel(IUnbakedModel unbakedModel) {
        if(unbakedModel instanceof BlockModel) {
            BlockModel model = (BlockModel)unbakedModel;
            return !model.customData.hasCustomGeometry();
        } else if((unbakedModel instanceof Multipart) || (unbakedModel instanceof VariantList))
            return true;
        else
            return false;
    }

    private IBakedModel bakeModel(ResourceLocation pLocation, IModelTransform pTransform, java.util.function.Function<RenderMaterial, net.minecraft.client.renderer.texture.TextureAtlasSprite> textureGetter) {
        IUnbakedModel iunbakedmodel = this.unbakedCache.get(pLocation);
        if(iunbakedmodel == null)
            throw new IllegalStateException("we should not be baking unloaded models");
        if (iunbakedmodel instanceof BlockModel) {
            BlockModel blockmodel = (BlockModel)iunbakedmodel;
            if (blockmodel.getRootModel() == GENERATION_MARKER) {
                return ITEM_MODEL_GENERATOR.generateBlockModel(textureGetter, blockmodel).bake((ModelBakery)(Object)this, blockmodel, this.atlasSet::getSprite, pTransform, pLocation, false);
            }
        }

        /* Ensure only one thread builds at a time if needed */
        if(canBakeParallel(iunbakedmodel)) {
            return iunbakedmodel.bake((ModelBakery)(Object)this, textureGetter, pTransform, pLocation);
        } else {
            synchronized(this.bakedCache) {
                return iunbakedmodel.bake((ModelBakery)(Object)this, textureGetter, pTransform, pLocation);
            }
        }

    }

    /**
     * @author embeddedt
     * @reason avoid rehashing model data when unneeded
     */
    @Overwrite(remap = false)
    public IBakedModel getBakedModel(ResourceLocation pLocation, IModelTransform pTransform, java.util.function.Function<RenderMaterial, net.minecraft.client.renderer.texture.TextureAtlasSprite> textureGetter) {
        /* Try to retrieve the model */
        IdentityHashMap<IModelTransform, IBakedModel> modelsForLocation = this.fasterBakedCache.computeIfAbsent(pLocation, loc -> new IdentityHashMap<>());
        synchronized (modelsForLocation) {
            IBakedModel result = modelsForLocation.get(pTransform);
            if(result != null)
                return result;
        }
        /* Otherwise, bake the model and then store it */
        synchronized(this.unbakedCache) {
            this.getModel(pLocation);
        }
        IBakedModel result = bakeModel(pLocation, pTransform, textureGetter);
        synchronized (modelsForLocation) {
            modelsForLocation.put(pTransform, result);
        }
        return result;
    }

    @Inject(method = "processLoading", at = @At(value = "INVOKE", target = "Lnet/minecraft/profiler/IProfiler;pop()V"))
    private void bakeModels(IProfiler pProfiler, int p_i226056_4_, CallbackInfo ci) {
        pProfiler.popPush("atlas");
        Minecraft.getInstance().executeBlocking(() -> {
            for(Pair<AtlasTexture, AtlasTexture.SheetData> pair : this.atlasPreparations.values()) {
                AtlasTexture atlastexture = pair.getFirst();
                AtlasTexture.SheetData atlastexture$sheetdata = pair.getSecond();
                atlastexture.reload(atlastexture$sheetdata);
            }
        });
        pProfiler.popPush("baking");
        StartupMessageManager.mcLoaderConsumer().ifPresent(c -> c.accept("Baking models"));
        this.atlasSet = new SpriteMap(this.atlasPreparations.values().stream().map(Pair::getFirst).collect(Collectors.toList()));
        this.bakedCache = Collections.emptyMap();
        this.fasterBakedCache = new ConcurrentHashMap<>();
        this.modelsToBakeParallel = this.unbakedCache.entrySet().stream()
                .collect(Collectors.partitioningBy(entry -> {
                    IUnbakedModel unbakedModel = entry.getValue();
                    if(unbakedModel == null)
                        return false;
                    else
                        return this.canBakeParallel(unbakedModel);
                }));
        List<Map.Entry<ResourceLocation, IUnbakedModel>> serialModels = this.modelsToBakeParallel.get(false);
        List<Map.Entry<ResourceLocation, IUnbakedModel>> parallelModels = this.modelsToBakeParallel.get(true);
        LOGGER.debug("Collected "
                + serialModels.size()
                + " serial models, "
                + parallelModels.size()
                + " parallel models");
        List<CompletableFuture<Pair<ResourceLocation, IBakedModel>>> futures = new ArrayList<>();
        /* First submit the parallel models */
        for(Map.Entry<ResourceLocation, IUnbakedModel> entry : parallelModels) {
            ResourceLocation loc = entry.getKey();
            futures.add(CompletableFuture.supplyAsync(() -> {
                IBakedModel ibakedmodel = null;

                try {
                    ibakedmodel = this.bake(loc, ModelRotation.X0_Y0);
                } catch (Exception exception) {
                    exception.printStackTrace();
                    LOGGER.warn("Unable to bake model: '{}': {}", loc, exception);
                }
                return ibakedmodel != null ? Pair.of(loc, ibakedmodel) : null;
            }, Util.backgroundExecutor()));
        }
        futures.forEach(future -> {
            Pair<ResourceLocation, IBakedModel> pair = future.join();
            if(pair != null && this.topLevelModels.containsKey(pair.getFirst()))
                this.bakedTopLevelModels.put(pair.getFirst(), pair.getSecond());
        });
        /* Then process serial models */
        serialModels.forEach((p_229350_1_) -> {
            IBakedModel ibakedmodel = null;
            ResourceLocation loc = p_229350_1_.getKey();

            try {
                ibakedmodel = this.bake(loc, ModelRotation.X0_Y0);
            } catch (Exception exception) {
                exception.printStackTrace();
                LOGGER.warn("Unable to bake model: '{}': {}", loc, exception);
            }

            if (ibakedmodel != null) {
                this.bakedTopLevelModels.put(loc, ibakedmodel);
            }
        });
        this.modelsToBakeParallel = null;
        this.atlasSet = null;
    }

    /**
     * @author embeddedt
     * @reason texture loading and baking are moved earlier in the launch process, only render thread stuff is done here
     */
    @Overwrite
    public SpriteMap uploadTextures(TextureManager pResourceManager, IProfiler pProfiler) {
        pProfiler.push("atlas_upload");

        for(Pair<AtlasTexture, AtlasTexture.SheetData> pair : this.atlasPreparations.values()) {
            AtlasTexture atlastexture = pair.getFirst();
            AtlasTexture.SheetData atlastexture$sheetdata = pair.getSecond();
            pResourceManager.register(atlastexture.location(), atlastexture);
            pResourceManager.bind(atlastexture.location());
            atlastexture.updateFilter(atlastexture$sheetdata);
        }
        this.atlasSet = new SpriteMap(this.atlasPreparations.values().stream().map(Pair::getFirst).collect(Collectors.toList()));
        pProfiler.pop();
        return this.atlasSet;
    }
}
