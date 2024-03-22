package org.embeddedt.modernfix.neoforge.mixin.perf.dynamic_resources.ctm;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.api.entrypoint.ModernFixClientIntegration;
import org.embeddedt.modernfix.neoforge.dynresources.IModelBakerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.chisel.ctm.CTM;
import team.chisel.ctm.api.model.IModelCTM;
import team.chisel.ctm.client.model.AbstractCTMBakedModel;
import team.chisel.ctm.client.model.ModelCTM;
import team.chisel.ctm.client.texture.IMetadataSectionCTM;
import team.chisel.ctm.client.util.ResourceUtil;
import team.chisel.ctm.client.util.TextureMetadataHandler;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

@Mixin(TextureMetadataHandler.class)
@RequiresMod("ctm")
@ClientOnlyMixin
public abstract class TextureMetadataHandlerMixin implements ModernFixClientIntegration {

    @Shadow(remap = false) @Nonnull protected abstract BakedModel wrap(UnbakedModel model, BakedModel object) throws IOException;

    @Shadow @Final private Multimap<ResourceLocation, Material> scrapedTextures;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void subscribeDynamic(CallbackInfo ci) {
        ModernFixClient.CLIENT_INTEGRATIONS.add(this);
    }

    @Inject(method = { "onModelBake(Lnet/neoforged/neoforge/client/event/ModelEvent$ModifyBakingResult;)V", "onModelBake(Lnet/neoforged/neoforge/client/event/ModelEvent$BakingCompleted;)V" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void noIteration(CallbackInfo ci) {
        ci.cancel();
    }

    @Override
    public BakedModel onBakedModelLoad(ResourceLocation rl, UnbakedModel rootModel, BakedModel baked, ModelState state, ModelBakery bakery) {
        if (rl instanceof ModelResourceLocation && !(baked instanceof AbstractCTMBakedModel) && !baked.isCustomRenderer()) {
            Deque<ResourceLocation> dependencies = new ArrayDeque<>();
            Set<ResourceLocation> seenModels = new HashSet<>();
            dependencies.push(rl);
            seenModels.add(rl);
            boolean shouldWrap = false;
            Set<Pair<String, String>> errors = new HashSet<>();
            // Breadth-first loop through dependencies, exiting as soon as a CTM texture is found, and skipping duplicates/cycles
            while (!shouldWrap && !dependencies.isEmpty()) {
                ResourceLocation dep = dependencies.pop();
                UnbakedModel model;
                try {
                    model = dep == rl ? rootModel : bakery.getModel(dep);
                } catch (Exception e) {
                    continue;
                }

                Collection<Material> textures = Sets.newHashSet(scrapedTextures.get(dep));
                Collection<ResourceLocation> newDependencies = model.getDependencies();
                for (Material tex : textures) {
                    IMetadataSectionCTM meta = null;
                    // Cache all dependent texture metadata
                    try {
                        meta = ResourceUtil.getMetadata(ResourceUtil.spriteToAbsolute(tex.texture())).orElse(null); // TODO, lazy
                    } catch (IOException e) {} // Fallthrough
                    if (meta != null) {
                        // At least one texture has CTM metadata, so we should wrap this model
                        shouldWrap = true;
                    }
                }

                for (ResourceLocation newDep : newDependencies) {
                    if (seenModels.add(newDep)) {
                        dependencies.push(newDep);
                    }
                }
            }
            if (shouldWrap) {
                try {
                    baked = wrap(rootModel, baked);
                    handleInit(rl, baked, bakery);
                    dependencies.clear();
                } catch (IOException e) {
                    CTM.logger.error("Could not wrap model " + rl + ". Aborting...", e);
                }
            }
        }
        return baked;
    }

    private void handleInit(ResourceLocation key, BakedModel wrappedModel, ModelBakery bakery) {
        if(wrappedModel instanceof AbstractCTMBakedModel baked) {
            IModelCTM var10 = baked.getModel();
            if (var10 instanceof ModelCTM ctmModel) {
                if (!ctmModel.isInitialized()) {
                    Function<Material, TextureAtlasSprite> spriteGetter = (m) -> {
                        return Minecraft.getInstance().getModelManager().getAtlas(m.atlasLocation()).getSprite(m.texture());
                    };
                    ModelBakery.ModelBakerImpl baker = bakery.new ModelBakerImpl((rl, m) -> {
                        return m.sprite();
                    }, key);
                    // bypass bakedCache so that dependent models get re-baked and thus retrieve their sprites again
                    ((IModelBakerImpl)baker).mfix$ignoreCache();
                    ctmModel.bake(baker, spriteGetter, BlockModelRotation.X0_Y0, key);
                }
            }
        }
    }
}
