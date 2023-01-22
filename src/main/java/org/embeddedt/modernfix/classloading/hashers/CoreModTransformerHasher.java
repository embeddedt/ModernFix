package org.embeddedt.modernfix.classloading.hashers;

import cpw.mods.modlauncher.ModernFixCachingClassTransformer;
import net.minecraftforge.coremod.CoreMod;
import net.minecraftforge.coremod.transformer.CoreModBaseTransformer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

public class CoreModTransformerHasher {
    private static final ConcurrentHashMap<CoreMod, byte[]> hashForCoremod;
    private static Field coremodField;

    static {
        hashForCoremod = new ConcurrentHashMap<>();
        try {
            coremodField = CoreModBaseTransformer.class.getDeclaredField("coreMod");
            coremodField.setAccessible(true);
        } catch(ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] hashCoreMod(CoreMod coreMod) {
        byte[] coreModContents;
        try {
            coreModContents = Files.readAllBytes(coreMod.getPath());
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
        MessageDigest hasher = ModernFixCachingClassTransformer.systemHasher.get();
        hasher.reset();
        return hasher.digest(coreModContents);
    }

    public static byte[] obtainHash(CoreModBaseTransformer<?> transformer) {
        CoreMod coremod;
        try {
            coremod = (CoreMod)coremodField.get(transformer);
        } catch(ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return hashForCoremod.computeIfAbsent(coremod, CoreModTransformerHasher::hashCoreMod);
    }
}
