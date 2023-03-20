package org.embeddedt.modernfix.mixin.perf.reduce_blockstate_cache_rebuilds;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.duck.IBlockState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin implements IBlockState {
    @Shadow public abstract void initCache();

    @Shadow private BlockBehaviour.BlockStateBase.Cache cache;

    private volatile boolean cacheInvalid = false;
    private static boolean buildingCache = false;
    private static final ThreadLocal<Boolean> isMakingCache = ThreadLocal.withInitial(() -> false);
    @Override
    public void clearCache() {
        cacheInvalid = true;
    }

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;cache:Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase$Cache;",
            ordinal = 0
    ))
    private BlockBehaviour.BlockStateBase.Cache initCacheIfNeeded(BlockBehaviour.BlockStateBase base) {
        if(cacheInvalid) {
            // Ensure that only one block's cache is built at a time
            synchronized (BlockBehaviour.BlockStateBase.Cache.class) {
                if(cacheInvalid) {
                    // Ensure that if we end up in here recursively, we just use the original cache
                    if(!buildingCache) {
                        buildingCache = true;
                        try {
                            this.initCache();
                            cacheInvalid = false;
                        } finally {
                            buildingCache = false;
                        }
                    }
                }

            }
        }
        return this.cache;
    }
}
