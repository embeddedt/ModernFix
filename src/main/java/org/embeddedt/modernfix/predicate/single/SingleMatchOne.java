package org.embeddedt.modernfix.predicate.single;

import net.minecraft.block.BlockState;
import net.minecraft.state.Property;

import java.util.List;
import java.util.function.Predicate;

public class SingleMatchOne implements Predicate<BlockState> {
    public final Property<?> property;
    public final Object value;

    public SingleMatchOne(Property<?> property, Object value) {
        this.property = property;
        this.value = value;
    }

    public static boolean areOfType(List<Predicate<BlockState>> predicates) {
        return predicates.stream()
                .allMatch(p -> {
                    return p instanceof SingleMatchOne;
                });
    }

    public static boolean valuesMatchType(List<Predicate<BlockState>> predicates, Class<?> type) {
        return predicates.stream()
                .allMatch(p -> {
                    return p instanceof SingleMatchOne && type.isInstance(((SingleMatchOne) p).value);
                });
    }

    @Override
    public boolean test(BlockState blockState) {
        return blockState.getValue(this.property) == this.value;
    }
}
