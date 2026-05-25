package org.embeddedt.modernfix.common.mixin.perf.release_protochunks;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ImposterProtoChunk.class)
public class ImposterProtoChunkMixin {
    @Shadow
    @Final
    private boolean allowWrites;

    /**
     * @author embeddedt
     * @reason This is a workaround for a very complicated and subtle vanilla issue. Vanilla uses ImposterProtoChunk as
     * a way of exposing fully generated chunks to other chunks that are still working on earlier generation stages.
     * The problem is that these fully generated chunks may be in two different states: promoted to FULL and already
     * visible to the level (with real BlockEntity objects), or in a loaded but not yet promoted state, where the postload
     * hook has not yet run to convert the NBT-serialized block entities from the disk into real BlockEntity objects.
     * The former state is the problematic one. If such a chunk is exposed to worldgen, features/structures may try
     * to interact with the block entity (e.g. by calling setChanged on it). This has the potential to deadlock.
     * <p></p>
     * The solution we use here is to simply hide the existence of any "real" BE that has a level attached from worldgen.
     * This is consistent with what other code would observe if the fully generated chunk were to be saved to disk
     * and then reloaded (ending up in the latter state), so it should not break well-behaved mods.
     * <p></p>
     * This problem occurs rather often with `mixin.perf.release_protochunks` enabled, because it significantly increases
     * the chance of a promoted LevelChunk wrapped in ImposterProtoChunk being used for world generation.
     */
    @ModifyExpressionValue(method = "getBlockEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
    private BlockEntity avoidLeakingLiveBE(BlockEntity original, @Local(ordinal = 0, argsOnly = true) BlockPos pos) {
        if (!this.allowWrites && original != null && original.getLevel() != null) {
            ModernFix.LOGGER.debug("Blocked accessing the main level BlockEntity at {} from the ImposterProtoChunk wrapper, as this is unsafe during worldgen.", pos, new Exception("Stacktrace"));
            return null;
        } else {
            return original;
        }
    }
}
