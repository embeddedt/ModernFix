package org.embeddedt.modernfix.tickables;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TickableObjectManager {
    private static final List<TickableObject> TICKABLE_OBJECT_LIST = new CopyOnWriteArrayList<>();

    public static void register(TickableObject object) {
        TICKABLE_OBJECT_LIST.add(object);
    }

    public static void runTick() {
        for(TickableObject o : TICKABLE_OBJECT_LIST) {
            o.tick();
        }
    }
}
