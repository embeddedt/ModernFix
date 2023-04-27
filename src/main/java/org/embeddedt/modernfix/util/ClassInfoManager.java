package org.embeddedt.modernfix.util;

import com.google.common.collect.ImmutableMap;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

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

        // Clear manifest entries
        int numManifestsCleared = 0;
        for(IModFileInfo mod : ModList.get().getModFiles()) {
            Manifest manifest = mod.getFile().getSecureJar().getManifest();
            if(manifest.getEntries() instanceof HashMap<String, Attributes> entryMap) {
                for (Map.Entry<String, Attributes> entry : entryMap.entrySet()) {
                    Attributes attributes = entry.getValue();
                    if (attributes.size() == 1 && attributes.getValue("SHA-256-Digest") != null) {
                        try {
                            entry.setValue(EmptyAttributes.INSTANCE);
                            numManifestsCleared++;
                        } catch (RuntimeException ignored) {
                        }
                    }
                }
            }
        }
        if(numManifestsCleared > 0)
            ModernFix.LOGGER.info("Cleared {} manifest attributes", numManifestsCleared);
    }

    static class EmptyAttributes extends Attributes {
        public static final EmptyAttributes INSTANCE = new EmptyAttributes();
        EmptyAttributes() {
            super(1);
            this.map = ImmutableMap.of();
        }
    }
}
