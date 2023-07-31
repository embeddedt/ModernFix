package org.embeddedt.modernfix.blockstate;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

public class FerriteCorePostProcess {
    private static final boolean willPostProcess;

    private static final MethodHandle theTable, toKeyIndex;

    static {
        boolean success = true;
        MethodHandle table = null, keyIndex = null;
        try {
            Class<?> fastMap = Class.forName("malte0811.ferritecore.fastmap.FastMap");
            Field field = fastMap.getDeclaredField("toKeyIndex");
            field.setAccessible(true);
            keyIndex = MethodHandles.publicLookup().unreflectSetter(field);
            field = StateHolder.class.getDeclaredField("ferritecore_globalTable");
            field.setAccessible(true);
            table = MethodHandles.publicLookup().unreflectGetter(field);
        } catch(ReflectiveOperationException | RuntimeException e) {
            e.printStackTrace();
            success = false;
        }
        willPostProcess = success;
        theTable = table;
        toKeyIndex = keyIndex;
    }

    private static final Object2IntMap<?> EMPTY_MAP = Object2IntMaps.unmodifiable(new Object2IntArrayMap<>());

    public static <O, S extends StateHolder<O, S>> void postProcess(StateDefinition<O, S> state) {
        if(!willPostProcess)
            return;
        try {
            if(state.getProperties().size() == 0) {
                for(S holder : state.getPossibleStates()) {
                    // deduplicate Object2IntMap objects from FerriteCore
                    // will probably be fixed upstream at some point, but likely not for older versions
                    Object table = theTable.invoke(holder);
                    toKeyIndex.invoke(table, EMPTY_MAP);
                }
            }
        } catch(Throwable e) {
            e.printStackTrace();
        }
    }
}
