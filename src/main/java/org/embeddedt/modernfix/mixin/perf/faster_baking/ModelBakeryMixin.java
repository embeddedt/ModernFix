package org.embeddedt.modernfix.mixin.perf.faster_baking;

import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.model.*;
import net.minecraft.client.renderer.model.multipart.Multipart;
import net.minecraft.client.renderer.model.multipart.Selector;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.texture.SpriteMap;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.profiler.IProfiler;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.vector.TransformationMatrix;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.api.EventListenerHelper;
import net.minecraftforge.eventbus.api.IEventListener;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.loading.progress.StartupMessageManager;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.core.config.ModernFixConfig;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.embeddedt.modernfix.models.LazyBakedModel;
import org.embeddedt.modernfix.util.ModUtil;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.embeddedt.modernfix.ModernFix.LOGGER;

@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin implements IExtendedModelBakery {
    @Shadow @Final private Map<ResourceLocation, IUnbakedModel> topLevelModels;

    @Shadow @Final private Map<ResourceLocation, IBakedModel> bakedTopLevelModels;

    @Shadow @Deprecated @Nullable public abstract IBakedModel bake(ResourceLocation pLocation, IModelTransform pTransform);

    @Shadow private Map<ResourceLocation, Pair<AtlasTexture, AtlasTexture.SheetData>> atlasPreparations;

    @Shadow @Nullable private SpriteMap atlasSet;

    @Shadow @Nullable public abstract IBakedModel getBakedModel(ResourceLocation pLocation, IModelTransform pTransform, Function<RenderMaterial, TextureAtlasSprite> textureGetter);

    @Shadow @Final public static ModelResourceLocation MISSING_MODEL_LOCATION;

    @Shadow @Final private Map<Triple<ResourceLocation, TransformationMatrix, Boolean>, IBakedModel> bakedCache;

    @Shadow @Final private Map<ResourceLocation, IUnbakedModel> unbakedCache;

    private IBakedModel bakeIfPossible(ResourceLocation p_229350_1_) {
        IBakedModel ibakedmodel = null;

        try {
            ibakedmodel = this.bake(p_229350_1_, ModelRotation.X0_Y0);
        } catch (Exception exception) {
            exception.printStackTrace();
            LOGGER.warn("Unable to bake model: '{}': {}", p_229350_1_, exception);
        }

        return ibakedmodel;
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
        IBakedModel missingModel = this.bake(MISSING_MODEL_LOCATION, ModelRotation.X0_Y0);
        this.bakedTopLevelModels.put(MISSING_MODEL_LOCATION, missingModel);
        Collection<String> modsListening = ModUtil.findAllModsListeningToEvent(ModelBakeEvent.class);
        LOGGER.debug("Found ModelBakeEvent listeners: [" + String.join(", ", modsListening) + "]");
        Set<String> incompatibleLazyBakedModels = ImmutableSet.<String>builder()
                .addAll(ModernFixConfig.MODELS_TO_BAKE.get())
                .addAll(modsListening)
                .build();
        /* First, bake any incompatible models ahead of time (for mods that have custom models) */
        this.unbakedCache.keySet().forEach(location -> {
           if(incompatibleLazyBakedModels.contains(location.getNamespace())) {
               this.bakeIfPossible(location);
           }
        });
        /* Then store them as top-level models if needed, and set up the lazy models */
        this.topLevelModels.keySet().forEach((p_229350_1_) -> {
            if(incompatibleLazyBakedModels.contains(p_229350_1_.getNamespace())) {
                IBakedModel model = this.bakeIfPossible(p_229350_1_);
                if(model != null)
                    this.bakedTopLevelModels.put(p_229350_1_, model);
            } else {
                this.bakedTopLevelModels.put(p_229350_1_, new LazyBakedModel(() -> {
                    synchronized (this.bakedCache) {
                        IBakedModel ibakedmodel = this.bakeIfPossible(p_229350_1_);

                        return ibakedmodel != null ? ibakedmodel : missingModel;
                    }
                }));
            }

        });
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
        pProfiler.pop();
        return this.atlasSet;
    }

    @Override
    public SpriteMap getUnfinishedAtlasSet() {
        return this.atlasSet;
    }
}
