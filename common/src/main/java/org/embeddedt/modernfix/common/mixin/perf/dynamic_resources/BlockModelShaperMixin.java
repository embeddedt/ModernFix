package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.level.block.state.BlockState;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynamicresources.ModelLocationCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;


@Mixin(BlockModelShaper.class)
@ClientOnlyMixin
public class BlockModelShaperMixin {
    @Shadow @Final private ModelManager modelManager;

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
