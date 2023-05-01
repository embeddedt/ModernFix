package org.embeddedt.modernfix.common.mixin.perf.compress_blockstate;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Material;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin extends StateHolder<Block, BlockState> {
    protected BlockStateBaseMixin(Block object, ImmutableMap<Property<?>, Comparable<?>> immutableMap, MapCodec<BlockState> mapCodec) {
        super(object, immutableMap, mapCodec);
    }

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;material:Lnet/minecraft/world/level/material/Material;"
    ))
    private Material getMaterial(BlockBehaviour.BlockStateBase base) {
        return this.owner.properties.material;
    }

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;destroySpeed:F"
    ))
    private float getDestroyTime(BlockBehaviour.BlockStateBase base) {
        return this.owner.properties.destroyTime;
    }

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;requiresCorrectToolForDrops:Z"
    ))
    private boolean getRequiresTool(BlockBehaviour.BlockStateBase base) {
        return this.owner.properties.requiresCorrectToolForDrops;
    }

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;canOcclude:Z"
    ))
    private boolean getCanOcclude(BlockBehaviour.BlockStateBase base) {
        return this.owner.properties.canOcclude;
    }

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;isRedstoneConductor:Lnet/minecraft/world/level/block/state/BlockBehaviour$StatePredicate;"
    ))
    private BlockBehaviour.StatePredicate getRedstoneConductor(BlockBehaviour.BlockStateBase base) {
        return this.owner.properties.isRedstoneConductor;
    }

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;isSuffocating:Lnet/minecraft/world/level/block/state/BlockBehaviour$StatePredicate;"
    ))
    private BlockBehaviour.StatePredicate getSuffocating(BlockBehaviour.BlockStateBase base) {
        return this.owner.properties.isSuffocating;
    }

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;isViewBlocking:Lnet/minecraft/world/level/block/state/BlockBehaviour$StatePredicate;"
    ))
    private BlockBehaviour.StatePredicate getViewBlocking(BlockBehaviour.BlockStateBase base) {
        return this.owner.properties.isViewBlocking;
    }

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;hasPostProcess:Lnet/minecraft/world/level/block/state/BlockBehaviour$StatePredicate;"
    ))
    private BlockBehaviour.StatePredicate getPostProcess(BlockBehaviour.BlockStateBase base) {
        return this.owner.properties.hasPostProcess;
    }

    @Redirect(method = "*", at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase;emissiveRendering:Lnet/minecraft/world/level/block/state/BlockBehaviour$StatePredicate;"
    ))
    private BlockBehaviour.StatePredicate getEmissiveRendering(BlockBehaviour.BlockStateBase base) {
        return this.owner.properties.emissiveRendering;
    }
}
