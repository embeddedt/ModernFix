package org.embeddedt.modernfix.classloading.hashers;

import com.google.common.collect.HashMultimap;
import com.google.common.io.Resources;
import org.spongepowered.asm.launch.IClassProcessor;
import org.spongepowered.asm.launch.MixinLaunchPluginLegacy;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class MixinTransformerHasher {
    private static HashMap<String, byte[]> hashesByClass = null;
    private final static MessageDigest hasher;

    static {
        try {
            hasher = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] obtainHash(MixinLaunchPluginLegacy plugin, String className) {
        synchronized (MixinTransformerHasher.class) {
            if(hashesByClass == null) {
                hashesByClass = new HashMap<>();
                HashMap<String, ArrayList<IMixinInfo>> mixinsByClass = new HashMap<>();
                try {
                    Field processorsField = MixinLaunchPluginLegacy.class.getDeclaredField("processors");
                    processorsField.setAccessible(true);
                    List<IClassProcessor> processors = (List<IClassProcessor>)processorsField.get(plugin);
                    Object transformHandler = null;
                    for(IClassProcessor processor : processors) {
                        if(processor.getClass().getName().equals("org.spongepowered.asm.service.modlauncher.MixinTransformationHandler")) {
                            transformHandler = processor;
                            break;
                        }
                    }
                    if(transformHandler == null)
                        throw new IllegalStateException("Mixin transform handler not found");
                    Field transformerField = transformHandler.getClass().getDeclaredField("transformer");
                    transformerField.setAccessible(true);
                    Object transformer = transformerField.get(transformHandler);
                    Field processorField = transformer.getClass().getDeclaredField("processor");
                    processorField.setAccessible(true);
                    Object processor = processorField.get(transformer);
                    Field configsField = processor.getClass().getDeclaredField("configs");
                    configsField.setAccessible(true);
                    List<?> configs = (List<?>)configsField.get(processor);
                    Field mixinsField = Class.forName("org.spongepowered.asm.mixin.transformer.MixinConfig").getDeclaredField("mixins");
                    mixinsField.setAccessible(true);
                    /* getTargetClasses can't be used because it's package-private */
                    Field classNamesField = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo").getDeclaredField("targetClassNames");
                    classNamesField.setAccessible(true);
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
                        hashesByClass.put(mixinsForClass.getKey().replace('/', '.'), hasher.digest());
                    }
                } catch(ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return hashesByClass.getOrDefault(className, new byte[0]);
    }
}
