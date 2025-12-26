package org.embeddedt.modernfix.util;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * List with no-op methods.
 */
public class DummyList<T> implements List<T> {
    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return Collections.emptyIterator();
    }

    @NotNull
    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @NotNull
    @Override
    public <T1> T1[] toArray(@NotNull T1[] t1s) {
        return Arrays.copyOf(t1s, 0);
    }

    @Override
    public boolean add(T t) {
        return false;
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> collection) {
        return false;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends T> collection) {
        return false;
    }

    @Override
    public boolean addAll(int i, @NotNull Collection<? extends T> collection) {
        return false;
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> collection) {
        return false;
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> collection) {
        return false;
    }

    @Override
    public void clear() {

    }

    @Override
    public T get(int i) {
        return null;
    }

    @Override
    public T set(int i, T t) {
        return null;
    }

    @Override
    public void add(int i, T t) {

    }

    @Override
    public T remove(int i) {
        return null;
    }

    @Override
    public int indexOf(Object o) {
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        return -1;
    }

    @NotNull
    @Override
    public ListIterator<T> listIterator() {
        return Collections.emptyListIterator();
    }

    @NotNull
    @Override
    public ListIterator<T> listIterator(int i) {
        return Collections.emptyListIterator();
    }

    @NotNull
    @Override
    public List<T> subList(int i, int i1) {
        return new DummyList<>();
    }
}
