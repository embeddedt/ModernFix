package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.IModelHoldingBlockState;
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

    @Shadow
    private Map<BlockState, BakedModel> modelByStateCache;

    @Inject(method = { "<init>", "replaceCache" }, at = @At("RETURN"))
    private void replaceModelMap(CallbackInfo ci) {
        // replace the backing map for mods which will access it
        this.modelByStateCache = new DynamicOverridableMap<>(state -> modelManager.getModel(ModelLocationCache.get(state)));
        // Clear the cached models on blockstate objects
        for(Block block : BuiltInRegistries.BLOCK) {
            for(BlockState state : block.getStateDefinition().getPossibleStates()) {
                if(state instanceof IModelHoldingBlockState modelHolder) {
                    modelHolder.mfix$setModel(null);
                }
            }
        }
    }

    private BakedModel cacheBlockModel(BlockState state) {
        // Do all model system accesses in the unlocked path
        ModelResourceLocation mrl = ModelLocationCache.get(state);
        BakedModel model = mrl == null ? null : modelManager.getModel(mrl);
        if (model == null) {
            model = modelManager.getMissingModel();
        }

        return model;
    }

    /**
     * @author embeddedt
     * @reason get the model from the dynamic model provider
     */
    @Overwrite
    public BakedModel getBlockModel(BlockState state) {
        if(state instanceof IModelHoldingBlockState modelHolder) {
            BakedModel model = modelHolder.mfix$getModel();

            if(model != null) {
                return model;
            }

            model = this.cacheBlockModel(state);
            modelHolder.mfix$setModel(model);
            return model;
        } else {
            return this.cacheBlockModel(state);
        }
    }
}
