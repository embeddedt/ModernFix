package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
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

    @Shadow
    private Map<BlockState, BakedModel> modelByStateCache;

    private ThreadLocal<Reference2ReferenceLinkedOpenHashMap<BlockState, BakedModel>> mfix$modelCache = ThreadLocal.withInitial(Reference2ReferenceLinkedOpenHashMap::new);

    @Inject(method = { "<init>", "replaceCache" }, at = @At("RETURN"))
    private void replaceModelMap(CallbackInfo ci) {
        // replace the backing map for mods which will access it
        this.modelByStateCache = new DynamicOverridableMap<>(state -> modelManager.getModel(ModelLocationCache.get(state)));
        this.mfix$modelCache = ThreadLocal.withInitial(Reference2ReferenceLinkedOpenHashMap::new);
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
        Reference2ReferenceLinkedOpenHashMap<BlockState, BakedModel> map = this.mfix$modelCache.get();
        BakedModel model = map.get(state);

        if(model != null) {
            return model;
        }

        model = this.cacheBlockModel(state);
        map.putAndMoveToFirst(state, model);
        if(map.size() > 500) {
            map.removeLast();
        }
        return model;
    }
}
