package org.embeddedt.modernfix.common.mixin.perf.cache_strongholds;

import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.embeddedt.modernfix.annotation.FeatureLevel;
import org.embeddedt.modernfix.annotation.RequiresFeatureLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConcentricRingsStructurePlacement.class)
@RequiresFeatureLevel(FeatureLevel.BETA)
public class ConcentricRingsStructurePlacementMixin {

    @Shadow @Final private int distance;
    @Shadow @Final private int spread;
    @Shadow @Final private int count;

    /**
     * Maximum per-axis section displacement from the initial ring chunk after biome snapping.
     *
     * Vanilla calls findBiomeHorizontal with radius=112 blocks. In quart space this is ±28,
     * and converting the selected quart back to section coordinates yields at most ±7 chunks
     * per axis from the original (initialX, initialZ).
     */
    @Unique private static final int MFIX_MAX_BIOME_SNAP_SECTIONS_PER_AXIS = 7;
    /**
     * Worst-case Euclidean error introduced by rounding:
     * initialX/Z = round(cos(angle) * dist), round(sin(angle) * dist).
     */
    @Unique private static final double MFIX_MAX_ROUNDING_ERROR = Math.sqrt(2.0) * 0.5;
    /**
     * Worst-case Euclidean biome-snap displacement when each axis can move by at most 7 chunks.
     */
    @Unique private static final double MFIX_MAX_BIOME_SNAP_ERROR = MFIX_MAX_BIOME_SNAP_SECTIONS_PER_AXIS * Math.sqrt(2.0);
    /**
     * Total conservative positional slack (rounding + biome snap) applied to radial bounds.
     */
    @Unique private static final double MFIX_MAX_POSITION_ERROR = MFIX_MAX_ROUNDING_ERROR + MFIX_MAX_BIOME_SNAP_ERROR;

    /** Squared chunk-distance below which no ring position can ever land. */
    @Unique private long mfix$innerRadiusSq;
    /** Squared chunk-distance above which no ring position can ever land. */
    @Unique private long mfix$outerRadiusSq;

    /**
     * Precomputes conservative radial bounds for vanilla's ring placement distance:
     * {@code dist = 4*i + i*i1*6 + noise}, where {@code i=distance} and {@code i1=circle}.
     *
     * - Inner bound uses the minimum possible base term ({@code i1=0} => {@code 4*i}).
     * - Outer bound uses the maximum reachable {@code i1} for this ({@code spread,count}) pair.
     *
     * Both bounds are expanded by {@link #MFIX_MAX_POSITION_ERROR} so we never reject a valid
     * chunk produced by rounding and biome snapping.
     */
    @Inject(
        method = "<init>(Lnet/minecraft/core/Vec3i;Lnet/minecraft/world/level/levelgen/structure/placement/StructurePlacement$FrequencyReductionMethod;FILjava/util/Optional;IIILnet/minecraft/core/HolderSet;)V",
        at = @At("RETURN")
    )
    private void mfix$computeRadiusBounds(CallbackInfo ci) {
        double maxNoise = this.distance * 1.25; // (nextDouble() - 0.5) * (distance * 2.5)

        // min(dist): 4*i + i*0*6 - maxNoise
        double minDist = 4.0 * this.distance - maxNoise;
        double safeInnerRadius = minDist - MFIX_MAX_POSITION_ERROR;
        this.mfix$innerRadiusSq = (long)Math.max(0.0, Math.floor(safeInnerRadius * safeInnerRadius));

        if (this.spread == 0) {
            // Vanilla behavior becomes non-finite here (angle += 2π / 0), so keep only inner rejection.
            this.mfix$outerRadiusSq = Long.MAX_VALUE;
            return;
        }

        int maxCircle = this.mfix$computeMaxCircleIndex();
        // max(dist): 4*i + i*maxCircle*6 + maxNoise
        double maxDist = 4.0 * this.distance + (double)this.distance * maxCircle * 6.0 + maxNoise;
        double safeOuterRadius = maxDist + MFIX_MAX_POSITION_ERROR;
        this.mfix$outerRadiusSq = (long)Math.ceil(safeOuterRadius * safeOuterRadius);
    }

    /**
     * Computes the highest ring index ({@code circle}) that vanilla can reach for this placement.
     *
     * This mirrors the spread/total update logic in
     * {@link net.minecraft.world.level.chunk.ChunkGeneratorStructureState#generateRingPositions},
     * but only tracks deterministic loop state (no RNG).
     */
    @Unique
    private int mfix$computeMaxCircleIndex() {
        int ringSpread = this.spread;
        int total = 0;
        int circle = 0;

        while (total + ringSpread < this.count) {
            total += ringSpread;
            circle++;
            ringSpread += 2 * ringSpread / (circle + 1);
            ringSpread = Math.min(ringSpread, this.count - total);
        }

        return circle;
    }

    /**
     * @author embeddedt, GPT-5.3-Codex
     * @reason Avoid calling getRingPositionsFor() when we know the current chunk lies outside the region where
     * concentric placement can even happen. This is particularly helpful when creating new worlds, because we can
     * avoid blocking on the slow noise computations within the spawn region around (0, 0).
     */
    @Inject(method = "isPlacementChunk", at = @At("HEAD"), cancellable = true)
    private void mfix$earlyRejectByRadius(ChunkGeneratorStructureState structureState, int x, int z,
                                          CallbackInfoReturnable<Boolean> cir) {
        long distSq = (long)x * x + (long)z * z;
        if (distSq < this.mfix$innerRadiusSq || distSq > this.mfix$outerRadiusSq) {
            cir.setReturnValue(false);
        }
    }
}
