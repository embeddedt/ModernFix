package org.embeddedt.modernfix.mixin.perf.nuke_empty_chunk_sections;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.palette.UpgradeData;
import net.minecraft.world.ITickList;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeContainer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(Chunk.class)
public class MixinChunk {
    @Shadow @Final private ChunkSection[] sections;

    @Inject(method = "<init>(Lnet/minecraft/world/World;" +
            "Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/world/biome/BiomeContainer;" +
            "Lnet/minecraft/util/palette/UpgradeData;Lnet/minecraft/world/ITickList;" +
            "Lnet/minecraft/world/ITickList;J[Lnet/minecraft/world/chunk/ChunkSection;Ljava/util/function/Consumer;)V",
            at = @At("RETURN"))
    private void reinit(World world, ChunkPos pos, BiomeContainer container, UpgradeData data,
                        ITickList list1, ITickList list2, long inhabited,
                        ChunkSection[] oldSections, Consumer consumer, CallbackInfo ci) {
        /* taken from Hydrogen */
        for(int i = 0; i < this.sections.length; i++) {
            if(ChunkSection.isEmpty(this.sections[i])) {
                this.sections[i] = null;
            }
        }
    }
}
