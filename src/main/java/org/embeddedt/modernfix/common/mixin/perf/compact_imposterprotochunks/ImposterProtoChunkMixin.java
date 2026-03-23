package org.embeddedt.modernfix.common.mixin.perf.compact_imposterprotochunks;

import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ImposterProtoChunk.class)
public abstract class ImposterProtoChunkMixin extends ChunkAccessMixin {
    /**
     * @author embeddedt
     * @reason ImposterProtoChunks allocate their own LevelChunkSection objects etc. which wastes quite
     * a bit of memory
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceDuplicateObjects(LevelChunk wrapped, boolean allowWrites, CallbackInfo ci) {
        this.sections = wrapped.getSections();
        this.skyLightSources = wrapped.getSkyLightSources();
    }
}
