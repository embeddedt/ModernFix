package org.embeddedt.modernfix.forge.mixin.bugfix.model_data_manager_cme;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.model.ModelDataManager;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Fix several concurrency issues in the default ModelDataManager.
 */
@Mixin(ModelDataManager.class)
@ClientOnlyMixin
public abstract class ModelDataManagerMixin {
    @Shadow protected static void refreshModelData(Level world, ChunkPos chunk) {
        throw new AssertionError();
    }

    @Shadow @Final private static Map<ChunkPos, Set<BlockPos>> needModelDataRefresh;

    /**
     * Make the set of positions to refresh a real concurrent hash set rather than relying on synchronizedSet,
     * because the returned iterator won't be thread-safe otherwise. See https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/7511
     */
    @ModifyArg(method = "requestModelDataRefresh", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;", ordinal = 0), index = 1, remap = false)
    private static Function<ChunkPos, Set<BlockPos>> changeTypeOfSetUsed(Function<ChunkPos, Set<BlockPos>> mappingFunction) {
        return pos -> Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    @Redirect(method = "getModelData(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;)Ljava/util/Map;", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/model/ModelDataManager;refreshModelData(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;)V"), remap = false)
    private static void onlyRefreshOnMainThread(Level toUpdate, ChunkPos pos) {
        // Only refresh model data on the main thread. This prevents calling getBlockEntity from worker threads
        // which could cause weird CMEs or other behavior.
        // Avoid the loop if no model data needs to be refreshed, to prevent unnecessary allocation.
        if(Minecraft.getInstance().isSameThread() && !needModelDataRefresh.isEmpty()) {
            // Refresh the given chunk, and all its neighbors. This is less efficient than the default code
            // but we have no choice since we need to not do refreshing on workers, and blocks might
            // try to access model data in neighboring chunks.
            for(int x = -1; x <= 1; x++) {
                for(int z = -1; z <= 1; z++) {
                    refreshModelData(toUpdate, new ChunkPos(pos.x + x, pos.z + z));
                }
            }
        }
    }
}
