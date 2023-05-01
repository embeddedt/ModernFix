package org.embeddedt.modernfix.mixin.bugfix.edge_chunk_not_saved;

import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/* https://github.com/SuperCoder7979/chunksavingfix-fabric/blob/main/src/main/java/supercoder79/chunksavingfix/mixin/MixinThreadedAnvilChunkStorage.java */
@Mixin(ChunkMap.class)
public class ChunkManagerMixin {
    // TODO: hits both at the moment- check and re-evaluate
    @ModifyArg(method = "saveAllChunks(Z)V", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;", ordinal = 0), require = 0)
    private Predicate<ChunkHolder> alwaysAccessibleFlush(Predicate<ChunkHolder> chunkHolder) {
        return c -> true;
    }
    @ModifyArg(method = "saveAllChunks(Z)V", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;", ordinal = 1), require = 0)
    private Predicate<ChunkAccess> allowProtoChunkFlush(Predicate<ChunkAccess> chunk) {
        return c -> c instanceof ProtoChunk || c instanceof ImposterProtoChunk || c instanceof LevelChunk;
    }

    @ModifyArg(method = "saveAllChunks(Z)V", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;", ordinal = 3), require = 0)
    private Predicate<ChunkHolder> alwaysAccessible(Predicate<ChunkHolder> chunkHolder) {
        return c -> true;
    }
}
