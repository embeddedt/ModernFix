package org.embeddedt.modernfix.common.mixin.bugfix.skip_redundant_saves;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
public class ChunkSerializerMixin {
    /**
     * @author embeddedt
     * @reason When reloading chunks from disk, they by definition normally don't need saving unless they've changed.
     */
    @Inject(method = "read", at = @At(value = "CONSTANT", args = "stringValue=PostProcessing", ordinal = 0))
    private static void updateUnsavedFlag(ServerLevel level, PoiManager poiManager, ChunkPos pos, CompoundTag tag, CallbackInfoReturnable<ProtoChunk> cir, @Local(ordinal = 0) ChunkAccess chunkaccess) {
        chunkaccess.setUnsaved(tag.getBoolean("shouldSave"));
    }
}
