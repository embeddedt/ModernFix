package org.embeddedt.modernfix.duck;

import net.minecraft.client.resources.model.BakedModel;

public interface IModelHoldingBlockState {
    BakedModel mfix$getModel();
    void mfix$setModel(BakedModel model);
}
