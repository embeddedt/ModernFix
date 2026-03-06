package org.embeddedt.modernfix.common.mixin.bugfix.chunk_deadlock;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Either;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.ModernFix;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.function.Function;

@Mixin(ChunkMap.class)
public abstract class ChunkMapLoadMixin {
    @Shadow
    @Nullable
    protected abstract ChunkHolder getVisibleChunkIfPresent(long l);

    @Shadow
    @Final
    private BlockableEventLoop<Runnable> mainThreadExecutor;

    @Unique
    private static final ThreadLocal<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> MFIX_SURROGATE_FUTURE = new ThreadLocal<>();

    @Unique
    private final ConcurrentLinkedQueue<Throwable> mfix$promotionExceptions = new ConcurrentLinkedQueue<>();

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
    @Redirect(method = "protoChunkToFullChunk", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;", ordinal = 0))
    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> createSurrogateFuture(
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> previousFuture,
            Function<? super Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>, ? extends Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> fn,
            Executor executor) {
        var surrogate = new CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>();
        // Unlike vanilla, we execute the promotion lambda in mainThreadExecutor, rather than within the context
        // of the task sorter. Doing this avoids deadlocking the sorter if a blocking chunk load is attempted
        // during chunk promotion. We still initially compose the future through the sorter's executor to stop promotion
        // from running earlier than it would in vanilla.
        previousFuture.thenComposeAsync(CompletableFuture::completedFuture, executor).thenApplyAsync(either -> {
            // running on thread that executes lambda body
            MFIX_SURROGATE_FUTURE.set(surrogate);
            try {
                return fn.apply(either);
            } finally {
                MFIX_SURROGATE_FUTURE.remove();
            }
        }, this.mainThreadExecutor).whenComplete((either, throwable) -> {
            if (throwable != null) {
                if (!surrogate.isDone()) {
                    surrogate.completeExceptionally(throwable);
                } else {
                    // The chunk has already become visible at FULL status, so we
                    // track the exception ourselves and manually rethrow it at the right point
                    // to trigger a server crash
                    this.mfix$promotionExceptions.add(throwable);
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
    @Inject(method = "lambda$protoChunkToFullChunk$34", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;runPostLoad()V"))
    private void completeSurrogateFuture(ChunkHolder holder, ChunkAccess p_214856_, CallbackInfoReturnable<ChunkAccess> cir,
                                         @Local(ordinal = 0) LevelChunk levelChunk) {
        var future = MFIX_SURROGATE_FUTURE.get();
        if (future != null) {
            future.complete(Either.left(levelChunk));
        }
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void reportDeferredPromotionException(CallbackInfo ci) {
        var throwable = this.mfix$promotionExceptions.poll();
        if (throwable == null) {
            return;
        }
        if (throwable instanceof ReportedException e) {
            throw e;
        } else {
            throw new ReportedException(CrashReport.forThrowable(throwable, "Exception during promotion of chunk to FULL status"));
        }
    }


    // we also preserve the legacy currentlyLoading field to keep Forge parity

    private static final Field currentlyLoadingField = ObfuscationReflectionHelper.findField(ChunkHolder.class, "currentlyLoading");

    private static void setCurrentlyLoading(ChunkHolder holder, LevelChunk value) {
        try {
            currentlyLoadingField.set(holder, value);
        } catch(ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    /**
     * Set currentlyLoading before calling runPostLoad and restore its old value afterwards. We track the old value
     * to avoid conflicting with Forge if/when this feature is added.
     */
    @WrapOperation(method = "lambda$protoChunkToFullChunk$34", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;runPostLoad()V"))
    private void setCurrentLoadingThenPostLoad(LevelChunk chunk, Operation<Void> operation) {
        ChunkHolder holder = this.getVisibleChunkIfPresent(chunk.getPos().toLong());
        if(holder != null) {
            LevelChunk prevLoading = null;
            try {
                prevLoading = (LevelChunk)currentlyLoadingField.get(holder);
            } catch(ReflectiveOperationException e) {
                e.printStackTrace();
            }
            try {
                setCurrentlyLoading(holder, chunk);
                operation.call(chunk);
            } finally {
                setCurrentlyLoading(holder, prevLoading);
            }
        } else {
            ModernFix.LOGGER.warn("Unable to find chunk holder for loading chunk");
            operation.call(chunk);
        }
    }
}
