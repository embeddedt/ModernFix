package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources.ldlib;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.client.ClientProxy;
import com.lowdragmc.lowdraglib.client.model.custommodel.CustomBakedModel;
import com.lowdragmc.lowdraglib.client.model.custommodel.LDLMetadataSection;
import com.lowdragmc.lowdraglib.client.model.forge.LDLRendererModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.api.entrypoint.ModernFixClientIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Mixin(ClientProxy.class)
@ClientOnlyMixin
@RequiresMod("ldlib")
public abstract class ClientProxyImplMixin implements ModernFixClientIntegration {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void registerIntegration(CallbackInfo ci) {
        ModernFixClient.CLIENT_INTEGRATIONS.add(this);
    }

    @Redirect(method = "modelBake", at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;", ordinal = 0), remap = false)
    private Set<?> disableLoop(Map<?, ?> map) {
        return Set.of();
    }

    @Override
    public BakedModel onBakedModelLoad(ModelResourceLocation mrl, UnbakedModel rootModel, BakedModel baked, ModelState state, ModelBakery bakery, ModelBakery.TextureGetter textureGetter) {
        if (baked == null) {
            return null;
        }
        if (rootModel != null) {
            if (baked instanceof LDLRendererModel) {
                return baked;
            }
            if (baked.isCustomRenderer()) { // Nothing we can add to builtin models
                return baked;
            }
            Deque<ResourceLocation> dependencies = new ArrayDeque<>();
            Set<ResourceLocation> seenModels = new HashSet<>();
            ResourceLocation rl = mrl.id();
            dependencies.push(rl);
            seenModels.add(rl);
            boolean shouldWrap = ClientProxy.WRAPPED_MODELS.getOrDefault(mrl, false);
            // Breadth-first loop through dependencies, exiting as soon as a CTM texture is found, and skipping duplicates/cycles
            while (!shouldWrap && !dependencies.isEmpty()) {
                ResourceLocation dep = dependencies.pop();
                UnbakedModel model;
                try {
                    model = dep == rl ? rootModel : bakery.getModel(dep);
                } catch (Exception e) {
                    continue;
                }
                try {
                    Set<Material> textures = new HashSet<>(ClientProxy.SCRAPED_TEXTURES.get(dep));
                    for (Material tex : textures) {
                        // Cache all dependent texture metadata
                        // At least one texture has CTM metadata, so we should wrap this baked
                        if (!LDLMetadataSection.getMetadata(LDLMetadataSection.spriteToAbsolute(tex.texture())).isMissing()) { // TODO lazy
                            shouldWrap = true;
                            break;
                        }
                    }
                    if (!shouldWrap) {
                        for (ResourceLocation newDep : model.getDependencies()) {
                            if (seenModels.add(newDep)) {
                                dependencies.push(newDep);
                            }
                        }
                    }
                } catch (Exception e) {
                    LDLib.LOGGER.error("Error loading baked dependency {} for baked {}. Skipping...", dep, rl, e);
                }
            }
            ClientProxy.WRAPPED_MODELS.put(mrl, shouldWrap);
            if (shouldWrap) {
                return new CustomBakedModel<>(baked);
            }
        }
        return baked;
    }
}
