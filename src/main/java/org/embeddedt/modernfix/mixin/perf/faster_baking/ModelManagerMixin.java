package org.embeddedt.modernfix.mixin.perf.faster_baking;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.renderer.texture.AtlasSet;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.duck.IExtendedModelBakery;
import org.embeddedt.modernfix.models.LazyBakedModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import javax.annotation.Nullable;
import java.util.Map;

@Mixin(ModelManager.class)
public class ModelManagerMixin {
    @Shadow @Nullable private AtlasSet atlases;

    @Shadow private Map<ResourceLocation, BakedModel> bakedRegistry;

    @Shadow private Object2IntMap<BlockState> modelGroups;

    @Shadow @Final private TextureManager textureManager;

    @Shadow private BakedModel missingModel;

    @Shadow @Final private BlockModelShaper blockModelShaper;

    @Inject(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Lnet/minecraft/client/resources/model/ModelBakery;", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;endTick()V"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void fireModelBakeEvent(ResourceManager pResourceManager, ProfilerFiller pProfiler, CallbackInfoReturnable<ModelBakery> cir, ModelBakery pObject) {
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
    protected void apply(ModelBakery pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        pProfiler.startTick();
        pProfiler.push("upload");
        this.atlases = pObject.uploadTextures(this.textureManager, pProfiler);
        pProfiler.pop();
        LazyBakedModel.allowBakeForFlags = true;
        pProfiler.endTick();
    }
}
