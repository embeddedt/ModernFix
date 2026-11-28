package org.embeddedt.modernfix.world.gen;

import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Decides whether a density function's value is independent of the block Y coordinate. Only vanilla node types
 * whose compute provably ignores Y are accepted; anything else, including modded types, counts as height-dependent.
 * Works on router functions as well as on their NoiseChunk-wrapped form, whose caches are {@code MarkerOrMarked}.
 */
public final class HeightInvariance {
    private HeightInvariance() {
    }

    public static boolean ignoresY(DensityFunction function) {
        return check(function, new IdentityHashMap<>());
    }

    private static boolean check(DensityFunction f, Map<Object, Boolean> seen) {
        Boolean known = seen.get(f);
        if (known != null) {
            return known;
        }
        // A cycle cannot be evaluated anyway; treat it as height-dependent.
        seen.put(f, false);
        boolean result = checkNode(f, seen);
        seen.put(f, result);
        return result;
    }

    private static boolean checkNode(DensityFunction f, Map<Object, Boolean> seen) {
        if (f instanceof DensityFunctions.HolderHolder holder) {
            return check(holder.function().value(), seen);
        } else if (f instanceof DensityFunctions.MarkerOrMarked marker) {
            return switch (marker.type()) {
                case FlatCache, Cache2D, CacheOnce -> check(marker.wrapped(), seen);
                default -> false;
            };
        } else if (f instanceof DensityFunctions.Constant || f instanceof DensityFunctions.ShiftA || f instanceof DensityFunctions.ShiftB) {
            // ShiftA and ShiftB sample their noise at y = 0.
            return true;
        } else if (f instanceof DensityFunctions.Noise noise) {
            return noise.yScale() == 0.0D;
        } else if (f instanceof DensityFunctions.ShiftedNoise noise) {
            return noise.yScale() == 0.0D && check(noise.shiftX(), seen) && check(noise.shiftY(), seen) && check(noise.shiftZ(), seen);
        } else if (f instanceof DensityFunctions.Ap2 ap2) {
            return check(ap2.argument1(), seen) && check(ap2.argument2(), seen);
        } else if (f instanceof DensityFunctions.MulOrAdd mulOrAdd) {
            return check(mulOrAdd.input(), seen);
        } else if (f instanceof DensityFunctions.Mapped mapped) {
            return check(mapped.input(), seen);
        } else if (f instanceof DensityFunctions.Clamp clamp) {
            return check(clamp.input(), seen);
        } else if (f instanceof DensityFunctions.RangeChoice choice) {
            return check(choice.input(), seen) && check(choice.whenInRange(), seen) && check(choice.whenOutOfRange(), seen);
        } else if (f instanceof DensityFunctions.Spline spline) {
            return checkSpline(spline.spline(), seen);
        }
        return false;
    }

    private static boolean checkSpline(CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> spline, Map<Object, Boolean> seen) {
        if (spline instanceof CubicSpline.Constant) {
            return true;
        } else if (spline instanceof CubicSpline.Multipoint<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> multipoint) {
            if (!check(multipoint.coordinate().function().value(), seen)) {
                return false;
            }
            for (CubicSpline<DensityFunctions.Spline.Point, DensityFunctions.Spline.Coordinate> value : multipoint.values()) {
                if (!checkSpline(value, seen)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
