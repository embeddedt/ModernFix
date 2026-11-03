package org.embeddedt.modernfix.world.gen;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.Map;

/**
 * The visitor a NoiseChunk wraps its router with. Results of mapAll on shared references (registry holders and
 * spline coordinates) are memoized by the identity of the referenced function, so each shared subtree is rebuilt
 * once per chunk rather than once per reference. This yields the same graph as vanilla, whose wrap() already
 * collapses every rebuilt copy of a subtree onto the instance produced the first time.
 */
public final class MemoizingDensityVisitor implements DensityFunction.Visitor {
    private final DensityFunction.Visitor delegate;
    public final Map<DensityFunction, DensityFunction> holders = new Reference2ObjectOpenHashMap<>();
    public final Map<DensityFunction, DensityFunctions.Spline.Coordinate> coordinates = new Reference2ObjectOpenHashMap<>();

    public MemoizingDensityVisitor(DensityFunction.Visitor delegate) {
        this.delegate = delegate;
    }

    @Override
    public DensityFunction apply(DensityFunction function) {
        return this.delegate.apply(function);
    }

    @Override
    public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
        return this.delegate.visitNoise(noise);
    }
}
