package org.embeddedt.modernfix.common.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.duck.IModelHoldingBlockState;
import org.spongepowered.asm.mixin.Mixin;

import java.lang.ref.SoftReference;

@Mixin(BlockBehaviour.BlockStateBase.class)
@ClientOnlyMixin
public class BlockStateBaseMixin implements IModelHoldingBlockState {
    private volatile SoftReference<BlockStateModel> mfix$model;

    @Override
    public BlockStateModel mfix$getModel() {
        var ref = mfix$model;
        return ref != null ? ref.get() : null;
    }

    @Override
    public void mfix$setModel(BlockStateModel model) {
        mfix$model = model != null ? new SoftReference<>(model) : null;
    }
}

