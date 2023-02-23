package org.embeddedt.modernfix.mixin.perf.faster_baking;

import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.MultiVariant;
import net.minecraft.client.renderer.block.model.multipart.MultiPart;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.AtlasSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.resources.ResourceLocation;
import com.mojang.math.Transformation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.fml.loading.progress.StartupMessageManager;
import org.apache.commons.lang3.tuple.Triple;
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

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;

@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin implements IExtendedModelBakery {
    @Shadow @Final private Map<ResourceLocation, UnbakedModel> topLevelModels;

    @Shadow @Final private Map<ResourceLocation, BakedModel> bakedTopLevelModels;

    @Shadow @Deprecated @Nullable public abstract BakedModel bake(ResourceLocation pLocation, ModelState pTransform);

    @Shadow private Map<ResourceLocation, Pair<TextureAtlas, TextureAtlas.Preparations>> atlasPreparations;

    @Shadow @Nullable private AtlasSet atlasSet;

    @Shadow @Final public static ModelResourceLocation MISSING_MODEL_LOCATION;

    @Shadow @Final private Map<Triple<ResourceLocation, Transformation, Boolean>, BakedModel> bakedCache;

    @Shadow @Final private Map<ResourceLocation, UnbakedModel> unbakedCache;

    private BakedModel bakeIfPossible(ResourceLocation p_229350_1_) {
        BakedModel ibakedmodel = null;

        try {
            ibakedmodel = this.bake(p_229350_1_, BlockModelRotation.X0_Y0);
        } catch (Exception exception) {
            exception.printStackTrace();
            LOGGER.warn("Unable to bake model: '{}': {}", p_229350_1_, exception);
        }

        return ibakedmodel;
    }

    private boolean requiresBake(UnbakedModel model) {
        if(model instanceof BlockModel && ((BlockModel)model).customData.hasCustomGeometry())
            return true;
        else
            return false;
    }

    @Inject(method = "processLoading", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V"))
    private void bakeModels(ProfilerFiller pProfiler, int p_i226056_4_, CallbackInfo ci) {
        pProfiler.popPush("atlas");
        Minecraft.getInstance().executeBlocking(() -> {
            for(Pair<TextureAtlas, TextureAtlas.Preparations> pair : this.atlasPreparations.values()) {
                TextureAtlas atlastexture = pair.getFirst();
                TextureAtlas.Preparations atlastexture$sheetdata = pair.getSecond();
                atlastexture.reload(atlastexture$sheetdata);
            }
        });
        pProfiler.popPush("baking");
        StartupMessageManager.mcLoaderConsumer().ifPresent(c -> c.accept("Baking models"));
        this.atlasSet = new AtlasSet(this.atlasPreparations.values().stream().map(Pair::getFirst).collect(Collectors.toList()));
        BakedModel missingModel = this.bake(MISSING_MODEL_LOCATION, BlockModelRotation.X0_Y0);
        this.bakedTopLevelModels.put(MISSING_MODEL_LOCATION, missingModel);
        Collection<String> modsListening = ModUtil.findAllModsListeningToEvent(ModelBakeEvent.class);
        LOGGER.debug("Found ModelBakeEvent listeners: [" + String.join(", ", modsListening) + "]");
        Set<String> incompatibleLazyBakedModels = ImmutableSet.<String>builder()
                .addAll(ModernFixConfig.MODELS_TO_BAKE.get())
                .addAll(modsListening)
                .build();
        /* First, bake any incompatible models ahead of time (for mods that have custom models) */
        new ArrayList<>(this.unbakedCache.keySet()).forEach(location -> {
           if(incompatibleLazyBakedModels.contains(location.getNamespace())) {
               this.bakeIfPossible(location);
           }
        });
        List<ResourceLocation> multiparts = new ArrayList<>();
        /* Then store them as top-level models if needed, and set up the lazy models */
        this.topLevelModels.forEach((location, value) -> {
            if (requiresBake(value) || incompatibleLazyBakedModels.contains(location.getNamespace())) {
                BakedModel model = this.bakeIfPossible(location);
                if (model != null)
                    this.bakedTopLevelModels.put(location, model);
            } else {
                if(value instanceof MultiPart || value instanceof MultiVariant) {
                    multiparts.add(location);
                } else {
                    this.bakedTopLevelModels.put(location, new LazyBakedModel(() -> {
                        synchronized (this.bakedCache) {
                            BakedModel ibakedmodel = this.bakeIfPossible(location);

                            return ibakedmodel != null ? ibakedmodel : missingModel;
                        }
                    }));
                }
            }
        });
        multiparts.forEach(location -> {
            BakedModel model = this.bakeIfPossible(location);
            if (model != null)
                this.bakedTopLevelModels.put(location, model);
        });
    }

    /**
     * @author embeddedt
     * @reason texture loading and baking are moved earlier in the launch process, only render thread stuff is done here
     */
    @Overwrite
    public AtlasSet uploadTextures(TextureManager pResourceManager, ProfilerFiller pProfiler) {
        pProfiler.push("atlas_upload");
        for(Pair<TextureAtlas, TextureAtlas.Preparations> pair : this.atlasPreparations.values()) {
            TextureAtlas atlastexture = pair.getFirst();
            TextureAtlas.Preparations atlastexture$sheetdata = pair.getSecond();
            pResourceManager.register(atlastexture.location(), atlastexture);
            pResourceManager.bindForSetup(atlastexture.location());
            atlastexture.updateFilter(atlastexture$sheetdata);
        }
        pProfiler.pop();
        return this.atlasSet;
    }

    @Override
    public AtlasSet getUnfinishedAtlasSet() {
        return this.atlasSet;
    }
}
