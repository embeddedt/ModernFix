package org.embeddedt.modernfix.predicate.any;

import net.minecraft.block.BlockState;
import net.minecraft.state.Property;
import org.apache.commons.lang3.ArrayUtils;
import org.embeddedt.modernfix.predicate.single.SingleMatchAny;

import java.util.List;
import java.util.function.Predicate;

public class AllMatchAnyObject implements Predicate<BlockState> {
    private final Property<?>[] properties;
    private final Object[][] values;

    public AllMatchAnyObject(List<Predicate<BlockState>> list) {
        int size = list.size();

        this.properties = new Property[size];
        this.values = new Object[size][];

        for (int i = 0; i < size; i++) {
            SingleMatchAny predicate = (SingleMatchAny) list.get(i);

            this.properties[i] = predicate.property;
            this.values[i] = predicate.values;
        }
    }

    @Override
    public boolean test(BlockState blockState) {
        for (int i = 0; i < this.properties.length; i++) {
            if (!ArrayUtils.contains(this.values[i], blockState.getValue(this.properties[i]))) {
                return false;
            }
        }

        return true;
    }
}
