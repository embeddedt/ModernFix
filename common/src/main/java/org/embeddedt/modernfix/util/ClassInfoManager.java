package org.embeddedt.modernfix.util;

import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.lang.reflect.Field;
import java.util.Map;

public class ClassInfoManager {
    private static Map<String, ClassInfo> classInfoCache = null;
    public static void clear() {
        if(!ModernFixMixinPlugin.instance.isOptionEnabled("perf.clear_mixin_classinfo.ClassInfoManager"))
            return;
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
            classInfoCache.entrySet().removeIf(entry -> !entry.getKey().equals("java/lang/Object") && (entry.getValue() == null || !entry.getValue().isMixin()));
        } catch(RuntimeException e) {
            e.printStackTrace();
        }
    }
}
