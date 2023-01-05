package org.embeddedt.modernfix.mixin.perf.reduce_blockstate_cache_rebuilds;

import net.minecraft.block.AbstractBlock;
import org.embeddedt.modernfix.duck.IBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class AbstractBlockStateMixin implements IBlockState {
    @Shadow @Nullable protected AbstractBlock.AbstractBlockState.Cache cache;

    @Override
    public void clearCache() {
        this.cache = null;
    }
}
