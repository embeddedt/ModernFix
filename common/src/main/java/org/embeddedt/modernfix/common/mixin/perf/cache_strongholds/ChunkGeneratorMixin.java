package org.embeddedt.modernfix.common.mixin.perf.cache_strongholds;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.duck.IServerLevel;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;
import org.embeddedt.modernfix.world.StrongholdLocationCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {
    @Shadow @Final private List<ChunkPos> strongholdPositions;

    @Inject(method = "generateStrongholds", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Lists;newArrayList()Ljava/util/ArrayList;", ordinal = 0, remap = false), cancellable = true)
    private void useCachedDataIfAvailable(CallbackInfo ci) {
        ServerLevel level = searchLevel();
        if(level == null) {
            ModernFix.LOGGER.error("Can't find server level for " + this);
            return;
        }
        StrongholdLocationCache cache = ((IServerLevel)level).mfix$getStrongholdCache();
        List<ChunkPos> positions = cache.getChunkPosList();
        if(positions.isEmpty())
            return;
        ModernFix.LOGGER.debug("Loaded stronghold cache for dimension {} with {} positions", level.dimension().location(), positions.size());
        this.strongholdPositions.addAll(positions);
        ci.cancel();
    }

    private ServerLevel searchLevel() {
        MinecraftServer server = ModernFixPlatformHooks.getCurrentServer();
        if(server != null) {
            ServerLevel ourLevel = null;
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getChunkSource().getGenerator() == ((ChunkGenerator) (Object) this)) {
                    ourLevel = level;
                    break;
                }
            }
            return ourLevel;
        } else
            return null;
    }

    @Inject(method = "generateStrongholds", at = @At("TAIL"))
    private void saveCachedData(CallbackInfo ci) {
        if(this.strongholdPositions.size() > 0) {
            ServerLevel level = searchLevel();
            if(level != null) {
                StrongholdLocationCache cache = ((IServerLevel)level).mfix$getStrongholdCache();
                cache.setChunkPosList(this.strongholdPositions);
                ModernFix.LOGGER.debug("Saved stronghold cache for dimension {}", level.dimension().location());
            }
        }
    }
}
