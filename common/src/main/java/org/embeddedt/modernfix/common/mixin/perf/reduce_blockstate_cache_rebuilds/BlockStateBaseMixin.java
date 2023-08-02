package org.embeddedt.modernfix.common.mixin.perf.reduce_blockstate_cache_rebuilds;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FluidState;
import org.embeddedt.modernfix.duck.IBlockState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin implements IBlockState {
    @Shadow public abstract void initCache();

    @Shadow private BlockBehaviour.BlockStateBase.Cache cache;
    @Shadow private FluidState fluidState;
    @Shadow private boolean isRandomlyTicking;

    private volatile boolean cacheInvalid = false;
    private static boolean buildingCache = false;
    @Override
    public void clearCache() {
        cacheInvalid = true;
    }

    @Override
    public boolean isCacheInvalid() {
        return cacheInvalid;
    }

    private BlockBehaviour.BlockStateBase.Cache generateCache(BlockBehaviour.BlockStateBase base) {
        if(cacheInvalid) {
            // Ensure that only one block's cache is built at a time
            synchronized (BlockBehaviour.BlockStateBase.class) {
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

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;cache:Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase$Cache;",
            ordinal = 0
    ))
    private BlockBehaviour.BlockStateBase.Cache dynamicCacheGen(BlockBehaviour.BlockStateBase base) {
        return generateCache(base);
    }

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;fluidState:Lnet/minecraft/world/level/material/FluidState;",
            ordinal = 0
    ), require = 0)
    private FluidState genCacheBeforeGettingFluid(BlockBehaviour.BlockStateBase base) {
        generateCache(base);
        return this.fluidState;
    }

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;isRandomlyTicking:Z",
            ordinal = 0
    ))
    private boolean genCacheBeforeGettingTicking(BlockBehaviour.BlockStateBase base) {
        generateCache(base);
        return this.isRandomlyTicking;
    }

    @Dynamic
    @Inject(method = "getPathNodeType", at = @At("HEAD"), require = 0, remap = false)
    private void generateCacheLithium(CallbackInfoReturnable<?> cir) {
        generateCache((BlockBehaviour.BlockStateBase)(Object)this);
    }

    @Dynamic
    @Inject(method = "getNeighborPathNodeType", at = @At("HEAD"), require = 0, remap = false)
    private void generateCacheLithium2(CallbackInfoReturnable<?> cir) {
        generateCache((BlockBehaviour.BlockStateBase)(Object)this);
    }

    @Dynamic
    @Inject(method = "getAllFlags", at = @At("HEAD"), require = 0, remap = false)
    private void generateCacheLithium3(CallbackInfoReturnable<?> cir) {
        generateCache((BlockBehaviour.BlockStateBase)(Object)this);
    }
}
