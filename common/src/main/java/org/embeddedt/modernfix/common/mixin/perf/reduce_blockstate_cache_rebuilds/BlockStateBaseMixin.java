package org.embeddedt.modernfix.common.mixin.perf.reduce_blockstate_cache_rebuilds;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.embeddedt.modernfix.duck.IBlockState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;


@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin extends StateHolder<Block, BlockState> implements IBlockState {
    protected BlockStateBaseMixin(Block object, Reference2ObjectArrayMap<Property<?>, Comparable<?>> immutableMap, MapCodec<BlockState> mapCodec) {
        super(object, immutableMap, mapCodec);
    }

    private static final FluidState MFIX$VANILLA_DEFAULT_FLUID = Fluids.EMPTY.defaultFluidState();

    @Shadow public abstract void initCache();

    @Shadow private BlockBehaviour.BlockStateBase.Cache cache;
    @Shadow private FluidState fluidState;
    @Shadow private boolean isRandomlyTicking;
    @Shadow @Deprecated private boolean legacySolid;

    @Shadow protected abstract BlockState asState();

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

    private void mfix$generateCache() {
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
    }

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;cache:Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase$Cache;",
            ordinal = 0
    ))
    private BlockBehaviour.BlockStateBase.Cache dynamicCacheGen(BlockBehaviour.BlockStateBase base) {
        mfix$generateCache();
        return this.cache;
    }

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;fluidState:Lnet/minecraft/world/level/material/FluidState;",
            ordinal = 0
    ), require = 0)
    private FluidState genCacheBeforeGettingFluid(BlockBehaviour.BlockStateBase base) {
        // don't generate the full cache here as mods will iterate for the fluid state a lot
        // assume blockstates will not change their contained fluidstate at runtime more than once
        // this is how Lithium's implementation used to work, so it should be fine
        if(this.cacheInvalid && this.fluidState == MFIX$VANILLA_DEFAULT_FLUID) {
            synchronized (BlockBehaviour.BlockStateBase.class) {
                if(!buildingCache) {
                    buildingCache = true;
                    try {
                        this.fluidState = ((BlockBehaviourInvoker)this.owner).invokeGetFluidState(this.asState());
                    } finally {
                        buildingCache = false;
                    }
                }
            }

        }
        return this.fluidState;
    }

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;isRandomlyTicking:Z",
            ordinal = 0
    ))
    private boolean genCacheBeforeGettingTicking(BlockBehaviour.BlockStateBase base) {
        if(this.cacheInvalid)
            return ((BlockBehaviourInvoker)this.owner).invokeIsRandomlyTicking(this.asState());
        return this.isRandomlyTicking;
    }

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;legacySolid:Z",
            ordinal = 0
    ))
    private boolean genCacheBeforeCheckingSolid(BlockBehaviour.BlockStateBase base) {
        mfix$generateCache();
        return this.legacySolid;
    }
}
