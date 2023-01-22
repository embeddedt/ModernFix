package org.embeddedt.modernfix.classloading.hashers;

import com.google.common.io.Resources;
import cpw.mods.modlauncher.ModernFixCachingClassTransformer;
import org.spongepowered.asm.launch.IClassProcessor;
import org.spongepowered.asm.launch.MixinLaunchPluginLegacy;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class MixinTransformerHasher {
    private static HashMap<String, byte[]> hashesByClass = null;
    private final static MessageDigest hasher;

    private static Field processorsListField, transformerField, processorField, environmentField;

    private static final byte[] NO_MIXINS = new byte[] {(byte)0xde, (byte)0xad, (byte)0xbe, (byte)0xef};

    static {
        try {
            hasher = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] obtainHash(MixinLaunchPluginLegacy plugin, String className) {
        /* FIXME runs too early right now, and therefore doesn't pick up the list of mixins correctly */
        synchronized (MixinTransformerHasher.class) {
            if(hashesByClass == null) {
                try {
                    if(processorsListField == null) {
                        processorsListField = MixinLaunchPluginLegacy.class.getDeclaredField("processors");
                        processorsListField.setAccessible(true);
                    }
                    List<IClassProcessor> processors = (List<IClassProcessor>) processorsListField.get(plugin);
                    Object transformHandler = null;
                    for(IClassProcessor processor : processors) {
                        if(processor.getClass().getName().equals("org.spongepowered.asm.service.modlauncher.MixinTransformationHandler")) {
                            transformHandler = processor;
                            break;
                        }
                    }
                    if(transformHandler == null)
                        throw new IllegalStateException("Mixin transform handler not found");
                    if(transformerField == null) {
                        transformerField = transformHandler.getClass().getDeclaredField("transformer");
                        transformerField.setAccessible(true);
                    }
                    Object transformer = transformerField.get(transformHandler);
                    if(processorField == null) {
                        processorField = transformer.getClass().getDeclaredField("processor");
                        processorField.setAccessible(true);
                    }
                    Object processor = processorField.get(transformer);
                    if(environmentField == null) {
                        environmentField = processor.getClass().getDeclaredField("currentEnvironment");
                        environmentField.setAccessible(true);
                    }
                    MixinEnvironment currentEnv = (MixinEnvironment)environmentField.get(processor);
                    if(currentEnv == null || currentEnv.getPhase() != MixinEnvironment.Phase.DEFAULT) {
                        return null; /* no hash obtained until mixin is ready */
                    }
                    Field configsField = processor.getClass().getDeclaredField("configs");
                    configsField.setAccessible(true);
                    List<?> configs = (List<?>)configsField.get(processor);
                    Field mixinsField = Class.forName("org.spongepowered.asm.mixin.transformer.MixinConfig").getDeclaredField("mixins");
                    mixinsField.setAccessible(true);
                    /* getTargetClasses can't be used because it's package-private */
                    Field classNamesField = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo").getDeclaredField("targetClassNames");
                    classNamesField.setAccessible(true);
                    HashMap<String, ArrayList<IMixinInfo>> mixinsByClass = new HashMap<>();
                    for(Object config : configs) {
                        List<? extends IMixinInfo> mixins = (List<? extends IMixinInfo>)mixinsField.get(config);
                        for(IMixinInfo mixin : mixins) {
                            List<String> targetClassNames = (List<String>)classNamesField.get(mixin);
                            for(String s : targetClassNames) {
                                mixinsByClass.computeIfAbsent(s, k -> new ArrayList<>()).add(mixin);
                            }
                        }
                    }
                    for(ArrayList<IMixinInfo> infos : mixinsByClass.values()) {
                        infos.sort((info1, info2) -> Comparator.<String>naturalOrder().compare(info1.getClassName(), info2.getClassName()));
                    }
                    /* Now go through each class name and hash it */
                    HashMap<String, byte[]> hashesByClassInit = new HashMap<>();
                    for(Map.Entry<String, ArrayList<IMixinInfo>> mixinsForClass : mixinsByClass.entrySet()) {
                        hasher.reset();
                        for(IMixinInfo mixin : mixinsForClass.getValue()) {
                            URL url = Thread.currentThread().getContextClassLoader().getResource(mixin.getClassName().replace('.', '/') + ".class");
                            if(url == null)
                                throw new IllegalStateException("Can't find " + mixin.getClassName());
                            byte[] bytecode;
                            try {
                                bytecode = Resources.asByteSource(url).read();
                            } catch(IOException e) {
                                throw new RuntimeException(e);
                            }
                            hasher.update(bytecode);
                        }
                        hashesByClassInit.put(mixinsForClass.getKey().replace('/', '.'), hasher.digest());
                    }
                    hashesByClass = hashesByClassInit;
                } catch(ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return hashesByClass.getOrDefault(className, NO_MIXINS);
    }
}
