package org.embeddedt.modernfix.mixin.perf.dynamic_resources.ctm;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.ForgeModelBakery;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.embeddedt.modernfix.dynamicresources.DynamicModelBakeEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.chisel.ctm.CTM;
import team.chisel.ctm.client.model.AbstractCTMBakedModel;
import team.chisel.ctm.client.texture.IMetadataSectionCTM;
import team.chisel.ctm.client.util.ResourceUtil;
import team.chisel.ctm.client.util.TextureMetadataHandler;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.*;

@Mixin(TextureMetadataHandler.class)
public abstract class TextureMetadataHandlerMixin {

    @Shadow @Nonnull protected abstract BakedModel wrap(ResourceLocation loc, UnbakedModel model, BakedModel object, ForgeModelBakery loader) throws IOException;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void subscribeDynamic(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.addListener(this::onDynamicModelBake);
    }

    public void onDynamicModelBake(DynamicModelBakeEvent event) {
        UnbakedModel rootModel = event.getUnbakedModel();
        BakedModel baked = event.getModel();
        ResourceLocation rl = event.getLocation();
        if (!(baked instanceof AbstractCTMBakedModel) && !baked.isCustomRenderer()) {
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
                    model = dep == rl ? rootModel : event.getModelLoader().getModel(dep);
                } catch (Exception e) {
                    continue;
                }

                Collection<Material> textures = model.getMaterials(event.getModelLoader()::getModel, errors);
                Collection<ResourceLocation> newDependencies = model.getDependencies();
                for (Material tex : textures) {
                    IMetadataSectionCTM meta = null;
                    // Cache all dependent texture metadata
                    try {
                        meta = ResourceUtil.getMetadata(ResourceUtil.spriteToAbsolute(tex.texture()));
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
                    event.setModel(wrap(rl, rootModel, baked, event.getModelLoader()));
                    dependencies.clear();
                } catch (IOException e) {
                    CTM.logger.error("Could not wrap model " + rl + ". Aborting...", e);
                }
            }
        }
    }
}
