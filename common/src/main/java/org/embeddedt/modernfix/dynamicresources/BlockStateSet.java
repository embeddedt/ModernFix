package org.embeddedt.modernfix.dynamicresources;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class BlockStateSet implements Set<BlockState> {
    private static final BlockStateSet INSTANCE = new BlockStateSet();

    private BlockStateSet() {

    }

    public static BlockStateSet instance() {
        return INSTANCE;
    }

    @Override
    public int size() {
        return Block.BLOCK_STATE_REGISTRY.size();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean contains(Object o) {
        return o instanceof BlockState;
    }

    @Override
    public @NotNull Iterator<BlockState> iterator() {
        return Block.BLOCK_STATE_REGISTRY.iterator();
    }

    @Override
    public @NotNull Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull <T> T[] toArray(@NotNull T[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(BlockState blockState) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        return c.stream().allMatch(o -> o instanceof BlockState);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends BlockState> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }
}
