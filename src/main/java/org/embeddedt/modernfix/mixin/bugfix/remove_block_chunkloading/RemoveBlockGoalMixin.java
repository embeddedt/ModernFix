package org.embeddedt.modernfix.mixin.bugfix.remove_block_chunkloading;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.ForgeEventFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RemoveBlockGoal.class)
public class RemoveBlockGoalMixin {
    @Shadow @Final private Mob removerMob;

    @Redirect(method = "canUse", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/ForgeHooks;canEntityDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private boolean fireGriefingEvent(Level level, BlockPos pos, LivingEntity entity) {
        return ForgeEventFactory.getMobGriefingEvent(level, entity);
    }

    @Redirect(method = "isValidTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
    private boolean checkBlockValidDestroyTarget(BlockState state, Block desiredBlock, LevelReader level, BlockPos pos) {
        if(!(state.canEntityDestroy(level, pos, this.removerMob) && ForgeEventFactory.onEntityDestroyBlock(this.removerMob, pos, state)))
            return false;
        return state.is(desiredBlock);
    }
}
