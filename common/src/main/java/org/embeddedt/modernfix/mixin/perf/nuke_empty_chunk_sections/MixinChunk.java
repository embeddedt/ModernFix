package org.embeddedt.modernfix.mixin.perf.nuke_empty_chunk_sections;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.TickList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(LevelChunk.class)
public class MixinChunk {
    @Shadow @Final private LevelChunkSection[] sections;

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/ChunkBiomeContainer;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/level/TickList;Lnet/minecraft/world/level/TickList;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Ljava/util/function/Consumer;)V",
            at = @At("RETURN"))
    private void reinit(Level world, ChunkPos pos, ChunkBiomeContainer container, UpgradeData data,
                        TickList list1, TickList list2, long inhabited,
                        LevelChunkSection[] oldSections, Consumer consumer, CallbackInfo ci) {
        /* taken from Hydrogen */
        for(int i = 0; i < this.sections.length; i++) {
            if(LevelChunkSection.isEmpty(this.sections[i])) {
                this.sections[i] = null;
            }
        }
    }
}
