package org.embeddedt.modernfix.util;

import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;

public class ClassInfoManager {
    private static Map<String, ClassInfo> classInfoCache = null;
    public static void clear() {
        if(classInfoCache == null) {
            try {
                Field field = ClassInfo.class.getDeclaredField("cache");
                field.setAccessible(true);
                classInfoCache = (Map<String, ClassInfo>)field.get(null);
            } catch(ReflectiveOperationException | RuntimeException e) {
                e.printStackTrace();
                return;
            }
        }
        try {
            classInfoCache.entrySet().removeIf(entry -> !entry.getKey().equals("java/lang/Object"));
        } catch(RuntimeException e) {
            e.printStackTrace();
        }
    }
}
