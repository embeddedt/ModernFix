package org.embeddedt.modernfix.dynresources;

import com.google.common.collect.Iterators;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Optimized alternative to {@link com.google.common.collect.Sets#union(Set, Set)} in cases where the sets
 * are known to be disjoint.
 * @param <T> element type
 */
public class DisjointSetUnion<T> extends AbstractSet<T> {
    private final Set<T> set1, set2;

    public DisjointSetUnion(Set<T> set1, Set<T> set2) {
        this.set1 = set1;
        this.set2 = set2;
        this.assertDisjoint();
    }

    private void assertDisjoint() {
        Set<T> iterate = set1.size() < set2.size() ? set1 : set2;
        Set<T> contains = set1 == iterate ? set2 : set1;
        for (T obj : iterate) {
            if (contains.contains(obj)) {
                throw new IllegalArgumentException("Provided sets are not disjoint");
            }
        }
    }

    @Override
    public Iterator<T> iterator() {
        return Iterators.concat(set1.iterator(), set2.iterator());
    }

    @Override
    public int size() {
        return set1.size() + set2.size();
    }

    @Override
    public boolean remove(Object o) {
        return set1.remove(o) || set2.remove(o);
    }

    @Override
    public boolean contains(Object o) {
        return set1.contains(o) || set2.contains(o);
    }

    @Override
    public int hashCode() {
        return set1.hashCode() + set2.hashCode();
    }
}
