package org.embeddedt.modernfix.util;

import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

public class ClassInfoManager {
    private static boolean hasRun = false;
    public static void clear() {
        if (!ModernFixMixinPlugin.instance.isOptionEnabled("perf.clear_mixin_classinfo.ClassInfoManager") || hasRun)
            return;
        hasRun = true;
        ModernFix.resourceReloadExecutor().execute(ClassInfoManager::doClear);
    }

    private static Field accessible(Field f) {
        f.setAccessible(true);
        return f;
    }

    private static void doClear() {
        Map<String, ClassInfo> classInfoCache;
        Field mixinField, stateField, classNodeField, methodsField, fieldsField;
        Class<?> stateClz;
        try {
            Field field = accessible(ClassInfo.class.getDeclaredField("cache"));
            classInfoCache = (Map<String, ClassInfo>) field.get(null);
            mixinField = accessible(ClassInfo.class.getDeclaredField("mixin"));
            methodsField = accessible(ClassInfo.class.getDeclaredField("methods"));
            fieldsField = accessible(ClassInfo.class.getDeclaredField("fields"));
            stateClz = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo$State");
            stateField = accessible(Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo").getDeclaredField("state"));
            classNodeField = accessible(stateClz.getDeclaredField("classNode"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            e.printStackTrace();
            return;
        }
        MixinEnvironment.getDefaultEnvironment().audit();
        try {
            ClassNode emptyNode = new ClassNode();
            classInfoCache.entrySet().removeIf(entry -> {
                if(entry.getKey().equals("java/lang/Object"))
                    return false;
                ClassInfo mixinClz = entry.getValue();
                try {
                    if(mixinClz.isMixin()) {
                        // clear classNode in MixinInfo.State
                        IMixinInfo theInfo = (IMixinInfo) mixinField.get(mixinClz);
                        Object state = stateField.get(theInfo);
                        if (state != null)
                            classNodeField.set(state, emptyNode);
                    }
                    // clear fields, methods
                    ((Collection<?>)methodsField.get(mixinClz)).clear();
                    ((Collection<?>)fieldsField.get(mixinClz)).clear();
                } catch (ReflectiveOperationException | RuntimeException e) {
                    e.printStackTrace();
                }
                return true;
            });
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        ModernFix.LOGGER.warn("Cleared mixin data structures");
    }
}
