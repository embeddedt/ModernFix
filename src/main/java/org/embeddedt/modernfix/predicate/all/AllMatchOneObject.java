package org.embeddedt.modernfix.predicate.all;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.embeddedt.modernfix.predicate.single.SingleMatchOne;

import java.util.List;
import java.util.function.Predicate;

public class AllMatchOneObject implements Predicate<BlockState> {
    private final Property<?>[] properties;
    private final Object[] values;

    public AllMatchOneObject(List<Predicate<BlockState>> list) {
        int size = list.size();

        this.properties = new Property[size];
        this.values = new Object[size];

        for (int i = 0; i < size; i++) {
            SingleMatchOne predicate = (SingleMatchOne) list.get(i);

            this.properties[i] = predicate.property;
            this.values[i] = predicate.value;
        }
    }

    @Override
    public boolean test(BlockState blockState) {
        for (int i = 0; i < this.properties.length; i++) {
            if (blockState.getValue(this.properties[i]) != this.values[i]) {
                return false;
            }
        }

        return true;
    }
}
