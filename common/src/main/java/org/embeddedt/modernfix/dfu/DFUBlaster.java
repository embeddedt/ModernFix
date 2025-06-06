package org.embeddedt.modernfix.dfu;

import org.embeddedt.modernfix.ModernFix;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public class DFUBlaster {
    private static final List<Map<?, ?>> TRACKED_MAPS = buildTrackedMaps();
    private static final long DELAY_TIME = TimeUnit.SECONDS.toNanos(60);
    private static final AtomicLong NEXT_WAKE_TIME = new AtomicLong(System.nanoTime() + DELAY_TIME);

    private static List<Map<?, ?>> buildTrackedMaps() {
        var list = new ArrayList<Map<?, ?>>();
        tryAdd(list, "com.mojang.datafixers.DSL$Instances", "TAGGED_CHOICE_TYPE_CACHE");
        tryAdd(list, "com.mojang.datafixers.functions.Fold", "HMAP_APPLY_CACHE");
        tryAdd(list, "com.mojang.datafixers.types.Type", "REWRITE_CACHE");
        return list;
    }

    private static void tryAdd(List<Map<?, ?>> list, String className, String fieldName) {
        try {
            Class<?> clz = Class.forName(className);
            Field field = clz.getDeclaredField(fieldName);
            field.setAccessible(true);
            if (Map.class.isAssignableFrom(field.getType())) {
                list.add((Map<?, ?>)field.get(null));
            } else {
                ModernFix.LOGGER.error("Field {} on class {} is not a map", field, className);
            }
        } catch (ReflectiveOperationException e) {
            ModernFix.LOGGER.error("Error tracking DFU field {} on class {}", fieldName, className, e);
        }
    }

    public static void blastMaps() {
        new CleanerThread().start();
    }

    public static void kick() {
        NEXT_WAKE_TIME.getAndAdd(DELAY_TIME);
    }

    static class CleanerThread extends Thread {
        CleanerThread() {
            this.setName("DFU cleaning thread");
            this.setPriority(1);
            this.setDaemon(true);
        }

        @Override
        public void run() {
            while(true) {
                long waitTime = NEXT_WAKE_TIME.get() - System.nanoTime();
                if (waitTime > 0) {
                    LockSupport.parkNanos(waitTime);
                }

                long lastStamp = NEXT_WAKE_TIME.get();
                if (System.nanoTime() >= lastStamp) {
                    // Nobody kicked the watchdog again, we should clear the maps
                    TRACKED_MAPS.forEach(Map::clear);
                    NEXT_WAKE_TIME.compareAndSet(lastStamp, System.nanoTime() + DELAY_TIME);
                }
                // Otherwise, we were told to wait for longer, so don't do anything
            }
        }
    }
}
