package org.embeddedt.modernfix.world.gen;

import com.mojang.datafixers.util.Pair;
import net.minecraft.world.level.biome.Climate;

import java.util.Arrays;
import java.util.List;

/**
 * Builds exactly the same tree as vanilla's {@code Climate.RTree.create}, since the tree shape decides which biome
 * wins at a given climate point.
 * <p>
 * Vanilla re-sorts every node's leaves once per axis, comparing midpoints lexicographically starting at that axis.
 * Leaves only tie if all seven midpoints match, and the sorts are stable, so the order for an axis never depends on
 * earlier sorts. Each axis is therefore sorted once up front, every node owns the same [from, to) segment of all
 * seven sorted arrays, and splitting a node into buckets is a linear stable partition.
 * <p>
 * This runs once per world load, mostly before the JIT has compiled it, so per-leaf work is kept in small methods
 * that get compiled early.
 */
public final class ClimateRTreeBuilder<T> {
    private static final int P = 7;
    /** Largest midpoint range per axis that uses a lookup table for ranks, instead of sorting. */
    private static final int MAX_RANK_TABLE = 1 << 16;

    private final int n;
    private final Climate.RTree.Leaf<T>[] leaves;
    /** Leaf boxes and midpoints, indexed as [axis][leaf]. */
    private final long[][] boxMin = new long[P][], boxMax = new long[P][], mid = new long[P][];
    /** Sum of absolute midpoints per leaf, used to order nodes with at most 6 children. */
    private final long[] absMidSum;
    private final long[] midLo = new long[P], midHi = new long[P];
    /** Box spanning all leaves, as 7 mins followed by 7 maxes. */
    private final long[] rootSpan = new long[P * 2];

    private final int[][] rank = new int[P][];
    /** Per axis, where each rank starts in a counting pass. */
    private final int[][] rankStart = new int[P][];
    /** Per axis, maps (mid - midLo) to rank + 1, or null if the range is too wide. */
    private final int[][] rankTable = new int[P][];

    /** Leaf ids sorted by each axis. */
    private final int[][] sorted = new int[P][];
    private final int[] bucketOf, scratch;

    @SuppressWarnings("unchecked")
    private ClimateRTreeBuilder(List<Pair<Climate.ParameterPoint, T>> points) {
        this.n = points.size();
        this.leaves = new Climate.RTree.Leaf[n];
        for (int d = 0; d < P; d++) {
            this.boxMin[d] = new long[n];
            this.boxMax[d] = new long[n];
            this.mid[d] = new long[n];
        }
        this.absMidSum = new long[n];
        this.bucketOf = new int[n];
        this.scratch = new int[n];
        Arrays.fill(this.midLo, Long.MAX_VALUE);
        Arrays.fill(this.midHi, Long.MIN_VALUE);
        Arrays.fill(this.rootSpan, 0, P, Long.MAX_VALUE);
        Arrays.fill(this.rootSpan, P, P * 2, Long.MIN_VALUE);
        for (int i = 0; i < n; i++) {
            addLeaf(i, points.get(i));
        }
        computeRanks();
        sortAxes();
    }

    public static <T> Climate.RTree.Node<T> build(List<Pair<Climate.ParameterPoint, T>> points) {
        ClimateRTreeBuilder<T> builder = new ClimateRTreeBuilder<>(points);
        int n = points.size();
        if (n == 1) {
            return builder.leaves[0];
        }
        if (n <= 6) {
            int[] identity = new int[n];
            for (int i = 0; i < n; i++) {
                identity[i] = i;
            }
            return builder.buildSmall(identity, 0, n, builder.rootSpan, 0);
        }
        return builder.build(0, n, builder.rootSpan, 0);
    }

    private void addLeaf(int i, Pair<Climate.ParameterPoint, T> pair) {
        Climate.ParameterPoint point = pair.getFirst();
        this.leaves[i] = new Climate.RTree.Leaf<>(point, pair.getSecond());
        setAxis(i, 0, point.temperature().min(), point.temperature().max());
        setAxis(i, 1, point.humidity().min(), point.humidity().max());
        setAxis(i, 2, point.continentalness().min(), point.continentalness().max());
        setAxis(i, 3, point.erosion().min(), point.erosion().max());
        setAxis(i, 4, point.depth().min(), point.depth().max());
        setAxis(i, 5, point.weirdness().min(), point.weirdness().max());
        setAxis(i, 6, point.offset(), point.offset());
        long sum = 0;
        for (int d = 0; d < P; d++) {
            sum += Math.abs(this.mid[d][i]);
        }
        this.absMidSum[i] = sum;
    }

    private void setAxis(int i, int d, long min, long max) {
        long m = (min + max) / 2L;
        this.boxMin[d][i] = min;
        this.boxMax[d][i] = max;
        this.mid[d][i] = m;
        if (m < this.midLo[d]) this.midLo[d] = m;
        if (m > this.midHi[d]) this.midHi[d] = m;
        if (min < this.rootSpan[d]) this.rootSpan[d] = min;
        if (max > this.rootSpan[P + d]) this.rootSpan[P + d] = max;
    }

    /**
     * Assigns each leaf a dense rank per axis, in midpoint order. Climate values usually cover a small range with
     * few distinct midpoints, so a lookup table avoids sorting; wide ranges fall back to a sort.
     */
    private void computeRanks() {
        long[][] distinct = new long[P][];
        int[] distinctCount = new int[P];
        for (int d = 0; d < P; d++) {
            this.rank[d] = new int[n];
            if (this.midHi[d] - this.midLo[d] < MAX_RANK_TABLE) {
                this.rankTable[d] = new int[(int)(this.midHi[d] - this.midLo[d]) + 1];
                distinct[d] = new long[16];
            }
        }
        for (int i = 0; i < n; i++) {
            collectDistinct(i, distinct, distinctCount);
        }
        for (int d = 0; d < P; d++) {
            if (this.rankTable[d] != null) {
                Arrays.sort(distinct[d], 0, distinctCount[d]);
                for (int r = 0; r < distinctCount[d]; r++) {
                    this.rankTable[d][(int)(distinct[d][r] - this.midLo[d])] = r + 1;
                }
            } else {
                long[] values = this.mid[d].clone();
                Arrays.sort(values);
                int count = 0;
                for (int i = 0; i < n; i++) {
                    if (count == 0 || values[i] != values[count - 1]) {
                        values[count++] = values[i];
                    }
                }
                distinct[d] = values;
                distinctCount[d] = count;
            }
            this.rankStart[d] = new int[distinctCount[d]];
        }
        for (int i = 0; i < n; i++) {
            assignRanks(i, distinct, distinctCount);
        }
        // turn per-rank counts into start offsets
        for (int d = 0; d < P; d++) {
            int[] start = this.rankStart[d];
            int sum = 0;
            for (int r = 0; r < start.length; r++) {
                int count = start[r];
                start[r] = sum;
                sum += count;
            }
        }
    }

    private void collectDistinct(int i, long[][] distinct, int[] distinctCount) {
        for (int d = 0; d < P; d++) {
            int[] table = this.rankTable[d];
            if (table == null) {
                continue;
            }
            int index = (int)(this.mid[d][i] - this.midLo[d]);
            if (table[index] == 0) {
                table[index] = -1;
                if (distinctCount[d] == distinct[d].length) {
                    distinct[d] = Arrays.copyOf(distinct[d], distinctCount[d] * 2);
                }
                distinct[d][distinctCount[d]++] = this.mid[d][i];
            }
        }
    }

    private void assignRanks(int i, long[][] distinct, int[] distinctCount) {
        for (int d = 0; d < P; d++) {
            int[] table = this.rankTable[d];
            int r = table != null ? table[(int)(this.mid[d][i] - this.midLo[d])] - 1
                    : Arrays.binarySearch(distinct[d], 0, distinctCount[d], this.mid[d][i]);
            this.rank[d][i] = r;
            this.rankStart[d][r]++;
        }
    }

    private void sortAxes() {
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        // radix sort for axis 0, least significant axis first
        for (int d = P - 1; d >= 0; d--) {
            order = countingPass(order, d);
        }
        this.sorted[0] = order;
        // the order for axis a is a stable pass by rank a over the order for axis a+1
        for (int a = P - 1; a >= 1; a--) {
            this.sorted[a] = countingPass(this.sorted[(a + 1) % P], a);
        }
    }

    /** Stable sort of the given leaf ids by their rank on axis d. */
    private int[] countingPass(int[] src, int d) {
        int[] next = this.rankStart[d].clone();
        int[] ranks = this.rank[d];
        int[] dst = new int[n];
        for (int i = 0; i < n; i++) {
            int id = src[i];
            dst[next[ranks[id]]++] = id;
        }
        return dst;
    }

    /**
     * Builds the subtree for [from, to) of the sorted arrays. Vanilla only ever recurses into the children of a
     * bucket, which are always leaves, so every call here works on leaves. The span of the node is known from the
     * parent, and incomingAxis is the parent's chosen axis, whose order vanilla passes down to the children.
     */
    @SuppressWarnings("unchecked")
    private Climate.RTree.Node<T> build(int from, int to, long[] span, int incomingAxis) {
        int count = to - from;
        if (count <= 6) {
            return buildSmall(this.sorted[incomingAxis], from, to, span, 0);
        }

        // Must match vanilla's floating point calculation exactly
        int bucketSize = (int)Math.pow(6.0D, Math.floor(Math.log((double)count - 0.01D) / Math.log(6.0D)));
        int bucketCount = (count + bucketSize - 1) / bucketSize;

        long bestCost = Long.MAX_VALUE;
        int bestAxis = -1;
        long[] bestBounds = null;
        long[] bounds = new long[bucketCount * P * 2];
        for (int axis = 0; axis < P; axis++) {
            long cost = computeBucketBounds(this.sorted[axis], from, to, bucketSize, bucketCount, bounds, span);
            // strict comparison so the lowest axis wins ties, as in vanilla
            if (bestCost > cost) {
                bestCost = cost;
                bestAxis = axis;
                bestBounds = bounds.clone();
            }
        }

        int[] bucketOrder = new int[bucketCount];
        for (int i = 0; i < bucketCount; i++) {
            bucketOrder[i] = i;
        }
        sortBuckets(bucketOrder, bestBounds, bestAxis);

        Climate.RTree.Node<T>[] children = new Climate.RTree.Node[bucketCount];
        int[] best = this.sorted[bestAxis];
        if (bucketSize <= 6) {
            // the children are small nodes that only need the best axis order, so there is nothing to partition
            for (int i = 0; i < bucketCount; i++) {
                int bucket = bucketOrder[i];
                int start = from + bucket * bucketSize;
                children[i] = buildSmall(best, start, Math.min(to, start + bucketSize), bestBounds, bucket * P * 2);
            }
        } else {
            // move each bucket's leaves into a contiguous segment of every sorted array, in final bucket order
            int[] bucketStart = new int[bucketCount];
            int offset = from;
            for (int i = 0; i < bucketCount; i++) {
                int bucket = bucketOrder[i];
                bucketStart[bucket] = offset;
                offset += Math.min(to, from + (bucket + 1) * bucketSize) - (from + bucket * bucketSize);
            }
            for (int bucket = 0, i = from; i < to; bucket++) {
                int end = Math.min(to, i + bucketSize);
                for (; i < end; i++) {
                    this.bucketOf[best[i]] = bucket;
                }
            }
            for (int axis = 0; axis < P; axis++) {
                partition(this.sorted[axis], from, to, bucketStart);
            }
            for (int i = 0; i < bucketCount; i++) {
                int bucket = bucketOrder[i];
                int start = bucketStart[bucket];
                int size = Math.min(to, from + (bucket + 1) * bucketSize) - (from + bucket * bucketSize);
                children[i] = size == 1 ? this.leaves[this.sorted[0][start]]
                        : build(start, start + size, Arrays.copyOfRange(bestBounds, bucket * P * 2, (bucket + 1) * P * 2), bestAxis);
            }
        }
        return new Climate.RTree.SubTree<>(toParameters(span, 0), Arrays.asList(children));
    }

    /** Builds a node with at most 6 leaves, which vanilla orders by the sum of absolute midpoints. */
    @SuppressWarnings("unchecked")
    private Climate.RTree.Node<T> buildSmall(int[] order, int from, int to, long[] bounds, int boundsOffset) {
        int count = to - from;
        if (count == 1) {
            return this.leaves[order[from]];
        }
        int[] ids = Arrays.copyOfRange(order, from, to);
        for (int i = 1; i < count; i++) {
            int value = ids[i];
            int j = i - 1;
            while (j >= 0 && this.absMidSum[ids[j]] > this.absMidSum[value]) {
                ids[j + 1] = ids[j];
                j--;
            }
            ids[j + 1] = value;
        }
        Climate.RTree.Node<T>[] children = new Climate.RTree.Node[count];
        for (int i = 0; i < count; i++) {
            children[i] = this.leaves[ids[i]];
        }
        return new Climate.RTree.SubTree<>(toParameters(bounds, boundsOffset), Arrays.asList(children));
    }

    /** Stable partition of order[from, to) by bucket, placing each bucket at its start offset. */
    private void partition(int[] order, int from, int to, int[] bucketStart) {
        int[] next = bucketStart.clone();
        for (int i = from; i < to; i++) {
            int id = order[i];
            this.scratch[next[this.bucketOf[id]]++] = id;
        }
        System.arraycopy(this.scratch, from, order, from, to - from);
    }

    /**
     * Writes each bucket's box into bounds as 7 mins followed by 7 maxes per bucket, and returns the total cost,
     * matching what vanilla computes from its throwaway subtrees.
     */
    private long computeBucketBounds(int[] order, int from, int to, int bucketSize, int bucketCount, long[] bounds, long[] span) {
        long total = 0;
        for (int bucket = 0; bucket < bucketCount; bucket++) {
            int start = from + bucket * bucketSize;
            int end = Math.min(to, start + bucketSize);
            int out = bucket * P * 2;
            for (int d = 0; d < P; d++) {
                long min = scanMin(order, start, end, this.boxMin[d], span[d]);
                long max = scanMax(order, start, end, this.boxMax[d], span[P + d]);
                bounds[out + d] = min;
                bounds[out + P + d] = max;
                total += Math.abs(max - min);
            }
        }
        return total;
    }

    /** Stops early on reaching the node's own minimum, since no bucket can go below it. */
    private static long scanMin(int[] order, int start, int end, long[] values, long floor) {
        long min = Long.MAX_VALUE;
        for (int i = start; i < end; i++) {
            long value = values[order[i]];
            if (value < min) {
                min = value;
                if (min == floor) {
                    break;
                }
            }
        }
        return min;
    }

    private static long scanMax(int[] order, int start, int end, long[] values, long ceiling) {
        long max = Long.MIN_VALUE;
        for (int i = start; i < end; i++) {
            long value = values[order[i]];
            if (value > max) {
                max = value;
                if (max == ceiling) {
                    break;
                }
            }
        }
        return max;
    }

    private static List<Climate.Parameter> toParameters(long[] bounds, int offset) {
        Climate.Parameter[] parameters = new Climate.Parameter[P];
        for (int d = 0; d < P; d++) {
            parameters[d] = new Climate.Parameter(bounds[offset + d], bounds[offset + P + d]);
        }
        return Arrays.asList(parameters);
    }

    /** Stable sort of buckets lexicographically by the absolute midpoints of their boxes, starting at the given axis. */
    private static void sortBuckets(int[] bucketOrder, long[] bounds, int axis) {
        for (int i = 1; i < bucketOrder.length; i++) {
            int value = bucketOrder[i];
            int j = i - 1;
            while (j >= 0 && compareBuckets(bucketOrder[j], value, bounds, axis) > 0) {
                bucketOrder[j + 1] = bucketOrder[j];
                j--;
            }
            bucketOrder[j + 1] = value;
        }
    }

    private static int compareBuckets(int left, int right, long[] bounds, int axis) {
        int leftBase = left * P * 2, rightBase = right * P * 2;
        for (int i = 0; i < P; i++) {
            int a = axis + i;
            if (a >= P) {
                a -= P;
            }
            long leftMid = Math.abs((bounds[leftBase + a] + bounds[leftBase + P + a]) / 2L);
            long rightMid = Math.abs((bounds[rightBase + a] + bounds[rightBase + P + a]) / 2L);
            int result = Long.compare(leftMid, rightMid);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }
}
