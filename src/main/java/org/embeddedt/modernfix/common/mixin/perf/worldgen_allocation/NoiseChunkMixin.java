package org.embeddedt.modernfix.common.mixin.perf.worldgen_allocation;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.embeddedt.modernfix.world.gen.MemoizingDensityVisitor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = NoiseChunk.class, priority = 100)
public abstract class NoiseChunkMixin {
    @Shadow protected abstract DensityFunction wrap(DensityFunction densityFunction);

    @Unique
    private MemoizingDensityVisitor mfix$wrapVisitor;

    @Unique
    private DensityFunction.Visitor mfix$getWrapVisitor() {
        MemoizingDensityVisitor visitor = this.mfix$wrapVisitor;
        if (visitor == null) {
            visitor = new MemoizingDensityVisitor(this::wrap);
            this.mfix$wrapVisitor = visitor;
        }
        return visitor;
    }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private DensityFunction.Visitor useMemoizingVisitorForRouter(DensityFunction.Visitor visitor) {
        return mfix$getWrapVisitor();
    }

    @ModifyArg(method = { "<init>", "cachedClimateSampler" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/DensityFunction;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/DensityFunction;"))
    private DensityFunction.Visitor useMemoizingVisitor(DensityFunction.Visitor visitor) {
        return mfix$getWrapVisitor();
    }
}
