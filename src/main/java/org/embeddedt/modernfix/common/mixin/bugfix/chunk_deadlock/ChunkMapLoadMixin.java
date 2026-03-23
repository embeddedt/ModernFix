package org.embeddedt.modernfix.common.mixin.bugfix.chunk_deadlock;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(ChunkStatusTasks.class)
public abstract class ChunkMapLoadMixin {
    @Unique
    private static final ThreadLocal<CompletableFuture<ChunkAccess>> MFIX_SURROGATE_FUTURE = new ThreadLocal<>();

    /**
     * @author embeddedt
     * @reason This redirect makes several changes to how full chunk promotion works. First of all, promotion runs
     * directly in the context of the main thread executor, rather than going through the priority sorter.
     * This change allows attempts to load other chunks from within the promotion lambda to succeed (important
     * for bad EntityJoinLevelEvent implementations to not deadlock the game). Second, it slightly alters the
     * semantics of protoChunkToFullChunk so that the FULL chunk future will be completed before postload
     * callbacks finish running. This change allows attempts to load the _same_ chunk in the promotion lambda to
     * succeed, as otherwise the future would block waiting for itself to complete.
     *
     * <p>This is a cleaner version of a similar trick used in ModernFix versions for 1.16, which deferred specifically
     * entity addition to happen outside the futures.
     */
    @Redirect(method = "full", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;", ordinal = 0))
    private static CompletableFuture<ChunkAccess> createSurrogateFuture(Supplier<ChunkAccess> supplier, Executor executor,
                                                                        @Local(ordinal = 0, argsOnly = true) WorldGenContext worldGenContext) {
        var surrogate = new CompletableFuture<ChunkAccess>();
        // Unlike vanilla, we execute the promotion lambda in mainThreadExecutor, rather than within the context
        // of the task sorter. Doing this avoids deadlocking the sorter if a blocking chunk load is attempted
        // during chunk promotion. We still initially compose the future through the sorter's executor to stop promotion
        // from running earlier than it would in vanilla.
        var mainThreadExecutor = ((ServerChunkCacheAccessor) worldGenContext.level().getChunkSource()).mfix$getMainThreadProcessor();
        CompletableFuture.runAsync(() -> {}, executor).thenApplyAsync($ -> {
            // running on thread that executes lambda body
            MFIX_SURROGATE_FUTURE.set(surrogate);
            try {
                return supplier.get();
            } finally {
                MFIX_SURROGATE_FUTURE.remove();
            }
        }, mainThreadExecutor).whenComplete((either, throwable) -> {
            if (throwable != null) {
                if (!surrogate.isDone()) {
                    surrogate.completeExceptionally(throwable);
                } else {
                    // The chunk has already become visible at FULL status, so we
                    // track the exception ourselves and manually rethrow it at the right point
                    // to trigger a server crash
                    var exc = new ReportedException(CrashReport.forThrowable(throwable, "Exception during promotion of chunk to FULL status"));
                    MinecraftServer.setFatalException(exc);
                }
            } else {
                surrogate.complete(either);
            }
        });
        // Return the surrogate
        return surrogate;
    }

    /**
     * @author embeddedt
     * @reason Complete the surrogate future as soon as basic promotion is done, and before we start loading entities
     * & block entities. This allows EntityJoinLevelEvent to read the current chunk.
     */
    @Inject(method = "lambda$full$2", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;runPostLoad()V"))
    private static void completeSurrogateFuture(CallbackInfoReturnable<ChunkAccess> cir, @Local(ordinal = 0) LevelChunk levelChunk) {
        var future = MFIX_SURROGATE_FUTURE.get();
        if (future != null) {
            future.complete(levelChunk);
        }
    }
}
