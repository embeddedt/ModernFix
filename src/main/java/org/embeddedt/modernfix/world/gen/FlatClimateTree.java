package org.embeddedt.modernfix.world.gen;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.world.level.biome.Climate;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.ObjIntConsumer;

/**
 * A copy of a {@code Climate.RTree} laid out in preorder in flat arrays, searched without recursion.
 * <p>
 * The search visits nodes in the same order and with the same strict comparisons as vanilla's
 * {@code SubTree.search}, seeded with the same last result, so it returns the same leaf including on exact ties.
 */
public final class FlatClimateTree<T> {
    private static final int P = 7;
    /**
     * Axes in the order they are summed. A node is pruned as soon as its partial sum reaches the best distance, so the
     * axes that most often rule a node out come first: depth (surface against cave biomes), then continentalness and
     * erosion. Offset is last; the target's offset coordinate is always 0.
     */
    private static final int[] AXIS_ORDER = {4, 2, 3, 5, 0, 1, 6};

    /** Per node, min and max for each axis in {@link #AXIS_ORDER}, interleaved. */
    private final long[] bounds;
    /** Per node, the index just past its subtree; for leaves, this is the next index. */
    private final int[] skip;
    /** Per node, the leaf it is, or null for inner nodes. */
    private final Climate.RTree.Leaf<T>[] leaves;
    private final Reference2IntOpenHashMap<Climate.RTree.Leaf<T>> leafIndex;
    private int next;

    @SuppressWarnings("unchecked")
    private FlatClimateTree(int count) {
        this.bounds = new long[count * P * 2];
        this.skip = new int[count];
        this.leaves = new Climate.RTree.Leaf[count];
        this.leafIndex = new Reference2IntOpenHashMap<>();
        this.leafIndex.defaultReturnValue(-1);
    }

    /**
     * Returns null if the tree has no leaves.
     * <p>
     * Subtrees without leaves are left out. Vanilla's search returns the incoming best leaf from them unchanged, so
     * skipping them gives the same result.
     *
     * @param indexAssigned receives each leaf and its index, which can be passed back to {@link #search} as a hint
     */
    @Nullable
    public static <T> FlatClimateTree<T> build(Climate.RTree.Node<T> root,
                                               Function<Climate.RTree.Node<T>, Climate.Parameter[]> parameterSpace,
                                               Function<Climate.RTree.SubTree<T>, Climate.RTree.Node<T>[]> children,
                                               ObjIntConsumer<Climate.RTree.Leaf<T>> indexAssigned) {
        int count = count(root, children);
        if (count == 0) {
            return null;
        }
        FlatClimateTree<T> tree = new FlatClimateTree<>(count);
        tree.fill(root, parameterSpace, children, indexAssigned);
        return tree;
    }

    /** Counts the nodes that will be stored, which excludes subtrees without leaves. */
    private static <T> int count(Climate.RTree.Node<T> node, Function<Climate.RTree.SubTree<T>, Climate.RTree.Node<T>[]> children) {
        if (!(node instanceof Climate.RTree.SubTree<T> subTree)) {
            return 1;
        }
        int total = 0;
        for (Climate.RTree.Node<T> child : children.apply(subTree)) {
            total += count(child, children);
        }
        return total == 0 ? 0 : total + 1;
    }

    private void fill(Climate.RTree.Node<T> node, Function<Climate.RTree.Node<T>, Climate.Parameter[]> parameterSpace,
                      Function<Climate.RTree.SubTree<T>, Climate.RTree.Node<T>[]> children,
                      ObjIntConsumer<Climate.RTree.Leaf<T>> indexAssigned) {
        if (count(node, children) == 0) {
            return;
        }
        int index = this.next++;
        Climate.Parameter[] space = parameterSpace.apply(node);
        for (int i = 0; i < P; i++) {
            Climate.Parameter parameter = space[AXIS_ORDER[i]];
            this.bounds[index * P * 2 + i * 2] = parameter.min();
            this.bounds[index * P * 2 + i * 2 + 1] = parameter.max();
        }
        if (node instanceof Climate.RTree.SubTree<T> subTree) {
            for (Climate.RTree.Node<T> child : children.apply(subTree)) {
                fill(child, parameterSpace, children, indexAssigned);
            }
        } else {
            Climate.RTree.Leaf<T> leaf = (Climate.RTree.Leaf<T>) node;
            this.leaves[index] = leaf;
            this.leafIndex.put(leaf, index);
            indexAssigned.accept(leaf, index);
        }
        this.skip[index] = this.next;
    }

    private static long axisDistance(long target, long min, long max) {
        long above = target - max;
        long below = min - target;
        return above > 0L ? above : Math.max(below, 0L);
    }

    /**
     * Equivalent to vanilla's {@code root.search(target.toParameterArray(), lastResult, Node::distance)}. A last result
     * from another tree is ignored.
     *
     * @param lastResultIndex the index reported for the last result at build time, or any value if unknown
     */
    public Climate.RTree.Leaf<T> search(Climate.TargetPoint target, @Nullable Climate.RTree.Leaf<T> lastResult, int lastResultIndex) {
        return this.leaves[searchIndex(target, lastResult, lastResultIndex)];
    }

    /**
     * Like {@link #search}, but returns the index of the leaf.
     */
    public int searchIndex(Climate.TargetPoint target, @Nullable Climate.RTree.Leaf<T> lastResult, int lastResultIndex) {
        long[] b = this.bounds;
        int[] skip = this.skip;
        int count = skip.length;
        if (count == 1) {
            return 0;
        }
        // Targets in AXIS_ORDER.
        long t0 = target.depth(), t1 = target.continentalness(), t2 = target.erosion();
        long t3 = target.weirdness(), t4 = target.temperature(), t5 = target.humidity();
        int best = -1;
        long bestDistance = Long.MAX_VALUE;
        if (lastResult != null) {
            best = lastResultIndex >= 0 && lastResultIndex < count && this.leaves[lastResultIndex] == lastResult
                    ? lastResultIndex : this.leafIndex.getInt(lastResult);
            if (best >= 0) {
                int o = best * P * 2;
                long a, d;
                a = axisDistance(t0, b[o], b[o + 1]);
                d = a * a;
                a = axisDistance(t1, b[o + 2], b[o + 3]);
                d += a * a;
                a = axisDistance(t2, b[o + 4], b[o + 5]);
                d += a * a;
                a = axisDistance(t3, b[o + 6], b[o + 7]);
                d += a * a;
                a = axisDistance(t4, b[o + 8], b[o + 9]);
                d += a * a;
                a = axisDistance(t5, b[o + 10], b[o + 11]);
                d += a * a;
                a = axisDistance(0L, b[o + 12], b[o + 13]);
                d += a * a;
                bestDistance = d;
            }
        }
        // The root is entered without being tested against the current best, as in vanilla.
        int node = 1;
        while (node < count) {
            int o = node * P * 2;
            long a, d;
            a = axisDistance(t0, b[o], b[o + 1]);
            d = a * a;
            if (d >= bestDistance) { node = skip[node]; continue; }
            a = axisDistance(t1, b[o + 2], b[o + 3]);
            d += a * a;
            if (d >= bestDistance) { node = skip[node]; continue; }
            a = axisDistance(t2, b[o + 4], b[o + 5]);
            d += a * a;
            if (d >= bestDistance) { node = skip[node]; continue; }
            a = axisDistance(t3, b[o + 6], b[o + 7]);
            d += a * a;
            if (d >= bestDistance) { node = skip[node]; continue; }
            a = axisDistance(t4, b[o + 8], b[o + 9]);
            d += a * a;
            if (d >= bestDistance) { node = skip[node]; continue; }
            a = axisDistance(t5, b[o + 10], b[o + 11]);
            d += a * a;
            if (d >= bestDistance) { node = skip[node]; continue; }
            a = axisDistance(0L, b[o + 12], b[o + 13]);
            d += a * a;
            if (d < bestDistance) {
                // Every subtree is non-empty, so only leaves end one index after themselves.
                if (skip[node] == node + 1) {
                    best = node;
                    bestDistance = d;
                }
                node++;
            } else {
                node = skip[node];
            }
        }
        return best;
    }

    public Climate.RTree.Leaf<T> leaf(int index) {
        return this.leaves[index];
    }
}
