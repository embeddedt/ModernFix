package org.embeddedt.modernfix.duck;

import net.minecraft.client.renderer.block.model.BlockStateModel;

public interface IModelHoldingBlockState {
    BlockStateModel mfix$getModel();
    void mfix$setModel(BlockStateModel model);
}
