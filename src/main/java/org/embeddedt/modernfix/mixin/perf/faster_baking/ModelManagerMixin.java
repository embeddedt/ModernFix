package org.embeddedt.modernfix.mixin.perf.faster_baking;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ModelBakery;
import net.minecraft.client.renderer.model.ModelManager;
import net.minecraft.client.renderer.texture.SpriteMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.embeddedt.modernfix.models.LazyBakedModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import javax.annotation.Nullable;
import java.util.Map;

@Mixin(ModelManager.class)
public class ModelManagerMixin {
    @Shadow @Nullable private SpriteMap atlases;

    @Shadow private Map<ResourceLocation, IBakedModel> bakedRegistry;

    @Shadow private Object2IntMap<BlockState> modelGroups;

    @Shadow @Final private TextureManager textureManager;

    @Shadow private IBakedModel missingModel;

    @Shadow @Final private BlockModelShapes blockModelShaper;

    @Inject(method = "prepare(Lnet/minecraft/resources/IResourceManager;Lnet/minecraft/profiler/IProfiler;)Lnet/minecraft/client/renderer/model/ModelBakery;", at = @At(value = "INVOKE", target = "Lnet/minecraft/profiler/IProfiler;endTick()V"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void fireModelBakeEvent(IResourceManager pResourceManager, IProfiler pProfiler, CallbackInfoReturnable<ModelBakery> cir, ModelLoader pObject) {
        pProfiler.push("modelevent");
        if (this.atlases != null) {
            Minecraft.getInstance().executeBlocking(() -> {
                this.atlases.close();
            });
        }
        this.atlases = ((IExtendedModelBakery)(Object)pObject).getUnfinishedAtlasSet();
        this.bakedRegistry = pObject.getBakedTopLevelModels();
        this.modelGroups = pObject.getModelGroups();
        this.missingModel = this.bakedRegistry.get(ModelBakery.MISSING_MODEL_LOCATION);
        net.minecraftforge.client.ForgeHooksClient.onModelBake((ModelManager)(Object)this, this.bakedRegistry, pObject);
        pProfiler.popPush("cache");
        this.blockModelShaper.rebuildCache();
        pProfiler.pop();
    }

    /**
     * @author embeddedt
     * @reason most of the code is moved to prepare()
     */
    @Overwrite
    protected void apply(ModelBakery pObject, IResourceManager pResourceManager, IProfiler pProfiler) {
        pProfiler.startTick();
        pProfiler.push("upload");
        this.atlases = pObject.uploadTextures(this.textureManager, pProfiler);
        pProfiler.pop();
        LazyBakedModel.allowBakeForFlags = true;
        pProfiler.endTick();
    }
}
