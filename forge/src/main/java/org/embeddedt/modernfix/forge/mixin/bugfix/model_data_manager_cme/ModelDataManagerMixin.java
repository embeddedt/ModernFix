package org.embeddedt.modernfix.forge.mixin.bugfix.model_data_manager_cme;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.client.model.data.ModelDataManager;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Fix several concurrency issues in the default ModelDataManager.
 */
@Mixin(ModelDataManager.class)
@ClientOnlyMixin
public abstract class ModelDataManagerMixin {
    @Shadow protected abstract void refreshAt(ChunkPos chunk);

    /**
     * Make the set of positions to refresh a real concurrent hash set rather than relying on synchronizedSet,
     * because the returned iterator won't be thread-safe otherwise. See https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/7511
     */
    @ModifyArg(method = "requestRefresh", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;", ordinal = 0), index = 1, remap = false)
    private static Function<ChunkPos, Set<BlockPos>> changeTypeOfSetUsed(Function<ChunkPos, Set<BlockPos>> mappingFunction) {
        return pos -> Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    @Redirect(method = "getAt(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/Map;", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/model/data/ModelDataManager;refreshAt(Lnet/minecraft/world/level/ChunkPos;)V"), remap = false)
    private void onlyRefreshOnMainThread(ModelDataManager instance, ChunkPos pos) {
        // Only refresh model data on the main thread. This prevents calling getBlockEntity from worker threads
        // which could cause weird CMEs or other behavior.
        if(Minecraft.getInstance().isSameThread()) {
            // Refresh the given chunk, and all its neighbors. This is less efficient than the default code
            // but we have no choice since we need to not do refreshing on workers, and blocks might
            // try to access model data in neighboring chunks.
            for(int x = -1; x <= 1; x++) {
                for(int z = -1; z <= 1; z++) {
                    refreshAt(new ChunkPos(pos.x + x, pos.z + z));
                }
            }
        }
    }
}
