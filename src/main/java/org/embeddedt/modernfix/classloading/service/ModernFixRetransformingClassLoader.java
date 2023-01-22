package org.embeddedt.modernfix.classloading.service;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import cpw.mods.gross.Java9ClassLoaderUtil;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * This becomes the new "system" classloader and is used to retransform ModLauncher as needed.
 */
public class ModernFixRetransformingClassLoader extends URLClassLoader {
    private static final ImmutableMap<String, BiFunction<String, byte[], byte[]>> TRANSFORMER_MAP = ImmutableMap.<String, BiFunction<String, byte[], byte[]>>builder()
            .put("cpw.mods.modlauncher.Launcher", ModernFixRetransformingClassLoader::transformModLauncher)
            .build();

    static {
        ClassLoader.registerAsParallelCapable();
    }
    private final ClassLoader resourceFinder;
    public ModernFixRetransformingClassLoader(ClassLoader resourceFinder) {
        super(Java9ClassLoaderUtil.getSystemClassPathURLs(), null);
        this.resourceFinder = resourceFinder;
    }

    private static byte[] transformModLauncher(String s, byte[] in) {
        return in;
    }

    @Override
    public Class<?> loadClass(String s) throws ClassNotFoundException {
        synchronized(this.getClassLoadingLock(s)) {
            if(!TRANSFORMER_MAP.containsKey(s)) {
                return super.loadClass(s);
            }
            byte[] classBytes;
            try {
                classBytes = Resources.toByteArray(this.resourceFinder.getResource(s.replace('.', '/') + ".class"));
            } catch(IOException e) {
                throw new ClassNotFoundException("Failed to load class bytes", e);
            }
            byte[] transformed = TRANSFORMER_MAP.get(s).apply(s, classBytes);
            return defineClass(s, transformed, 0, transformed.length);
        }
    }
}
