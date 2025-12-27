package org.embeddedt.modernfix.common.mixin.perf.worldgen_allocation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.embeddedt.modernfix.world.gen.PositionalBiomeGetter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Function;
import java.util.function.Supplier;

@Mixin(targets = {"net/minecraft/world/level/levelgen/SurfaceRules$Context"}, priority = 100)
public class SurfaceRulesContextMixin {
    @Shadow private long lastUpdateY;

    @Shadow private int blockY;

    @Shadow private int waterHeight;

    @Shadow private int stoneDepthBelow;

    @Shadow private int stoneDepthAbove;

    @Shadow private Supplier<Holder<Biome>> biome;

    @Shadow @Final private Function<BlockPos, Holder<Biome>> biomeGetter;

    @Shadow @Final private BlockPos.MutableBlockPos pos;

    /**
     * @author embeddedt
     * @reason Reuse supplier object instead of creating new ones every time
     */
    @Overwrite
    public void updateY(int stoneDepthAbove, int stoneDepthBelow, int waterHeight, int blockX, int blockY, int blockZ) {
        ++this.lastUpdateY;
        var getter = this.biome;
        if(getter == null) {
            this.biome = getter = new PositionalBiomeGetter(this.biomeGetter, this.pos);
        }
        ((PositionalBiomeGetter)getter).update(blockX, blockY, blockZ);
        this.blockY = blockY;
        this.waterHeight = waterHeight;
        this.stoneDepthBelow = stoneDepthBelow;
        this.stoneDepthAbove = stoneDepthAbove;
    }
}
