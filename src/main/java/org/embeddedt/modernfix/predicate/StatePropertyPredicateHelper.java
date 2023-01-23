package org.embeddedt.modernfix.predicate;

import me.jellysquid.mods.hydrogen.common.state.all.AllMatchOneBoolean;
import net.minecraft.block.BlockState;
import org.embeddedt.modernfix.predicate.all.AllMatchOneObject;
import org.embeddedt.modernfix.predicate.any.AllMatchAnyObject;
import org.embeddedt.modernfix.predicate.single.SingleMatchAny;
import org.embeddedt.modernfix.predicate.single.SingleMatchOne;

import java.util.List;
import java.util.function.Predicate;

public class StatePropertyPredicateHelper {
    @SuppressWarnings("unchecked")
    public static Predicate<BlockState> allMatch(List<Predicate<BlockState>> predicates) {
        if (SingleMatchOne.areOfType(predicates)) {
            if (SingleMatchOne.valuesMatchType(predicates, Boolean.class)) {
                return new AllMatchOneBoolean(predicates);
            }

            return new AllMatchOneObject(predicates);
        } else if (SingleMatchAny.areOfType(predicates)) {
            return new AllMatchAnyObject(predicates);
        }

        return new AllPredicate<>(predicates.toArray(new Predicate[0]));
    }

    @SuppressWarnings("unchecked")
    public static Predicate<BlockState> anyMatch(List<Predicate<BlockState>> predicates) {
        return new AnyPredicate<>(predicates.toArray(new Predicate[0]));
    }
}
