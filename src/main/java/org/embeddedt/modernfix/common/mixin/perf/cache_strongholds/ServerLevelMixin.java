package org.embeddedt.modernfix.common.mixin.perf.cache_strongholds;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.embeddedt.modernfix.duck.IChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    /**
     * @author embeddedt
     * @reason Make the dimension path accessible to ChunkGeneratorStructureState.
     */
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;ensureStructuresGenerated()V"))
    private void setCachePath(ChunkGeneratorStructureState instance, Operation<Void> original,
                                     @Local(ordinal = 0, argsOnly = true) LevelStorageSource.LevelStorageAccess levelStorageAccess,
                                     @Local(ordinal = 0, argsOnly = true) ResourceKey<Level> dimension,
                                     @Local(ordinal = 0, argsOnly = true) MinecraftServer server) {
        ((IChunkGenerator)instance).mfix$setStrongholdCachePath(levelStorageAccess.getDimensionPath(dimension), server.registryAccess());
        original.call(instance);
    }
}
