package org.embeddedt.modernfix.mixin.bugfix.edge_chunk_not_saved;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.ChunkPrimerWrapper;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/* https://github.com/SuperCoder7979/chunksavingfix-fabric/blob/main/src/main/java/supercoder79/chunksavingfix/mixin/MixinThreadedAnvilChunkStorage.java */
@Mixin(ChunkManager.class)
public class ChunkManagerMixin {
    // TODO: hits both at the moment- check and re-evaluate
    @ModifyArg(method = "saveAllChunks(Z)V", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;", ordinal = 0), require = 0)
    private Predicate<ChunkHolder> alwaysAccessibleFlush(Predicate<ChunkHolder> chunkHolder) {
        return c -> true;
    }
    @ModifyArg(method = "saveAllChunks(Z)V", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;", ordinal = 1), require = 0)
    private Predicate<IChunk> allowProtoChunkFlush(Predicate<IChunk> chunk) {
        return c -> c instanceof ChunkPrimer || c instanceof ChunkPrimerWrapper || c instanceof Chunk;
    }

    @ModifyArg(method = "saveAllChunks(Z)V", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;", ordinal = 3), require = 0)
    private Predicate<ChunkHolder> alwaysAccessible(Predicate<ChunkHolder> chunkHolder) {
        return c -> true;
    }
}
