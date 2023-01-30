package org.embeddedt.modernfix.mixin.perf.faster_baking;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.model.*;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.texture.SpriteMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.profiler.IProfiler;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.loading.progress.StartupMessageManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin {
    @Shadow @Final private Map<ResourceLocation, IUnbakedModel> topLevelModels;

    @Shadow @Final private Map<ResourceLocation, IBakedModel> bakedTopLevelModels;

    @Shadow @Deprecated @Nullable public abstract IBakedModel bake(ResourceLocation pLocation, IModelTransform pTransform);

    @Shadow @Final private static Logger LOGGER;

    @Shadow private Map<ResourceLocation, Pair<AtlasTexture, AtlasTexture.SheetData>> atlasPreparations;

    @Shadow @Nullable private SpriteMap atlasSet;

    @Shadow @Final private Map<ResourceLocation, IUnbakedModel> unbakedCache;
    private Map<Boolean, List<ResourceLocation>> modelsToBakeParallel;

    private boolean canBakeParallel(IUnbakedModel unbakedModel) {
        return false;
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
        this.modelsToBakeParallel = this.topLevelModels.keySet().stream()
                .collect(Collectors.partitioningBy(location -> {
                    return true;
                    /*
                    IUnbakedModel unbakedModel = this.unbakedCache.get(location);
                    if(unbakedModel == null)
                        return false;
                    else
                        return this.canBakeParallel(unbakedModel);
                     */
                }));
        List<ResourceLocation> parallelModels = this.modelsToBakeParallel.get(true);
        parallelModels.forEach((p_229350_1_) -> {
            IBakedModel ibakedmodel = null;

            try {
                ibakedmodel = this.bake(p_229350_1_, ModelRotation.X0_Y0);
            } catch (Exception exception) {
                exception.printStackTrace();
                LOGGER.warn("Unable to bake model: '{}': {}", p_229350_1_, exception);
            }

            if (ibakedmodel != null) {
                this.bakedTopLevelModels.put(p_229350_1_, ibakedmodel);
            }
        });
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
        /*
        StartupMessageManager.mcLoaderConsumer().ifPresent(c -> c.accept("Baking incompatible models"));
        List<ResourceLocation> serialModels = this.modelsToBakeParallel.get(false);
        serialModels.forEach((p_229350_1_) -> {
            IBakedModel ibakedmodel = null;

            try {
                ibakedmodel = this.bake(p_229350_1_, ModelRotation.X0_Y0);
            } catch (Exception exception) {
                exception.printStackTrace();
                LOGGER.warn("Unable to bake model: '{}': {}", p_229350_1_, exception);
            }

            if (ibakedmodel != null) {
                this.bakedTopLevelModels.put(p_229350_1_, ibakedmodel);
            }
        });
         */
        this.modelsToBakeParallel = null;
        pProfiler.pop();
        return this.atlasSet;
    }
}
