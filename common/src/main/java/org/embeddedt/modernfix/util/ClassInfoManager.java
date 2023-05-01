package org.embeddedt.modernfix.util;

import com.google.common.collect.ImmutableMap;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.security.CodeSigner;
import java.util.*;
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
        // TODO port
        /*
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

         */
        if(numManifestsCleared > 0)
            ModernFix.LOGGER.info("Cleared {} manifest attributes", numManifestsCleared);

        try {
            clearSecureJarStructs();
        } catch(Throwable e) {
            ModernFix.LOGGER.error("Couldn't clear Jar structs", e);
        }

    }

    private static void clearSecureJarStructs() throws Throwable {
        /*
        // Clear Jar signing data
        Unsafe unsafe;
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = (Unsafe)f.get(null);

        Field statusDataField, pendingSignersField, verifiedSignersField;
        statusDataField = Jar.class.getDeclaredField("statusData");
        pendingSignersField = Jar.class.getDeclaredField("pendingSigners");
        verifiedSignersField = Jar.class.getDeclaredField("verifiedSigners");

        long statusDataOffset = unsafe.objectFieldOffset(statusDataField);
        long pendingSignersOffset = unsafe.objectFieldOffset(pendingSignersField);
        long verifiedSignersOffset = unsafe.objectFieldOffset(verifiedSignersField);

        for(IModFileInfo mod : ModList.get().getModFiles()) {
            SecureJar secureJar = mod.getFile().getSecureJar();
            if(secureJar instanceof Jar) {
                unsafe.putObject(secureJar, statusDataOffset, LyingStatusDataMap.INSTANCE);
                unsafe.putObject(secureJar, pendingSignersOffset, EmptyCodeSignerTable.INSTANCE);
                unsafe.putObject(secureJar, verifiedSignersOffset, EmptyCodeSignerTable.INSTANCE);
            }
        }

         */
    }

    static class EmptyCodeSignerTable extends Hashtable<String, CodeSigner[]> {
        public static final EmptyCodeSignerTable INSTANCE = new EmptyCodeSignerTable();
        private static final CodeSigner[] VAL = new CodeSigner[0];

        @Override
        public synchronized CodeSigner[] put(String key, CodeSigner[] value) {
            return null;
        }

        @Override
        public synchronized boolean isEmpty() {
            return true;
        }

        @Override
        public synchronized boolean containsKey(Object key) {
            return false;
        }

        @Override
        public synchronized CodeSigner[] get(Object key) {
            return VAL;
        }
    }

    /**
     * This map is used to replace the statusData map.
     *
     * The lying in containsKey is intentionally done to force certain code paths to run in Jar.
     * Otherwise the security information might be recomputed many times.
     */
    static class LyingStatusDataMap implements Map<String, Object> {
        public static final LyingStatusDataMap INSTANCE = new LyingStatusDataMap();
        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean containsKey(Object o) {
            return true;
        }

        @Override
        public boolean containsValue(Object o) {
            return false;
        }

        @Override
        public Object get(Object o) {
            return null;
        }

        @Nullable
        @Override
        public Object put(String s, Object o) {
            return null;
        }

        @Override
        public Object remove(Object o) {
            return null;
        }

        @Override
        public void putAll(@NotNull Map<? extends String, ?> map) {

        }

        @Override
        public void clear() {
        }

        @NotNull
        @Override
        public Set<String> keySet() {
            return Collections.emptySet();
        }

        @NotNull
        @Override
        public Collection<Object> values() {
            return Collections.emptyList();
        }

        @NotNull
        @Override
        public Set<Entry<String, Object>> entrySet() {
            return Collections.emptySet();
        }
    }

    static class EmptyAttributes extends Attributes {
        public static final EmptyAttributes INSTANCE = new EmptyAttributes();
        EmptyAttributes() {
            super(1);
            this.map = ImmutableMap.of();
        }
    }
}
