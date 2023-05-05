package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.level.block.state.BlockState;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynamicresources.ModelLocationCache;
import org.embeddedt.modernfix.util.DynamicOverridableMap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;


@Mixin(BlockModelShaper.class)
@ClientOnlyMixin
public class BlockModelShaperMixin {
    @Shadow @Final private ModelManager modelManager;

    @Shadow @Final @Mutable
    private Map<BlockState, BakedModel> modelByStateCache;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceModelMap(CallbackInfo ci) {
        // replace the backing map for mods which will access it
        this.modelByStateCache = new DynamicOverridableMap<>(state -> modelManager.getModel(ModelLocationCache.get(state)));
    }

    /**
     * @author embeddedt
     * @reason no need to rebuild model cache, and location cache is done elsewhere
     */
    @Overwrite
    public void rebuildCache() {
    }

    @Overwrite
    public BakedModel getBlockModel(BlockState state) {
        BakedModel model = modelManager.getModel(ModelLocationCache.get(state));
        if (model == null) {
            model = modelManager.getMissingModel();
        }
        return model;
    }
}
