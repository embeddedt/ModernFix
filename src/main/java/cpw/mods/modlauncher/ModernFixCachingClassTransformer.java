package cpw.mods.modlauncher;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerActivity;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.minecraftforge.coremod.transformer.CoreModBaseTransformer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.classloading.api.IHashableTransformer;
import org.embeddedt.modernfix.classloading.hashers.CoreModTransformerHasher;
import org.embeddedt.modernfix.classloading.hashers.MixinTransformerHasher;
import org.objectweb.asm.Type;
import org.spongepowered.asm.launch.MixinLaunchPluginLegacy;

import javax.lang.model.SourceVersion;

public class ModernFixCachingClassTransformer extends ClassTransformer {
    private static final Logger LOGGER = LogManager.getLogger("ModernFixCachingTransformer");

    private static final File CLASS_CACHE_DAT = childFile(FMLPaths.GAMEDIR.get().resolve("modernfix").resolve("classTransformerV1.cache").toFile());
    private final LaunchPluginHandler pluginHandler;
    private final TransformStore transformStore;
    private final TransformerAuditTrail auditTrail;
    private final TransformingClassLoader transformingClassLoader;
    private final HashMap<String, List<ITransformer<?>>> transformersByClass;

    private static final int MAX_NUM_CLASSES = 10000;

    private ConcurrentHashMap<String, Pair<List<byte[]>, byte[]>> transformationCache;

    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private static File childFile(File file) {
        file.getParentFile().mkdirs();
        return file;
    }

    public static boolean isValidClassName(String className) {
        final String DOT_PACKAGE_INFO = ".package-info";
        if(className.endsWith(DOT_PACKAGE_INFO)) {
            className = className.substring(0, className.length() - DOT_PACKAGE_INFO.length());
        }
        return SourceVersion.isName(className);
    }

    public ModernFixCachingClassTransformer(TransformStore transformStore, LaunchPluginHandler pluginHandler, TransformingClassLoader transformingClassLoader, TransformerAuditTrail trail) {
        super(transformStore, pluginHandler, transformingClassLoader, trail);
        this.transformStore = transformStore;
        this.pluginHandler = pluginHandler;
        this.transformingClassLoader = transformingClassLoader;
        this.auditTrail = trail;
        /* Build a lookup table of all transformers for a given class */
        this.transformersByClass = new HashMap<>();
        try {
            Field transformersByTypeField = TransformStore.class.getDeclaredField("transformers");
            transformersByTypeField.setAccessible(true);
            Field transformersMapField = TransformList.class.getDeclaredField("transformers");
            transformersMapField.setAccessible(true);
            EnumMap<TransformTargetLabel.LabelType, TransformList<?>> transformersByType = (EnumMap<TransformTargetLabel.LabelType, TransformList<?>>)transformersByTypeField.get(this.transformStore);
            for(TransformList<?> transformList : transformersByType.values()) {
                Map<TransformTargetLabel, List<ITransformer<?>>> transformers = (Map<TransformTargetLabel, List<ITransformer<?>>>)transformersMapField.get(transformList);
                for(Map.Entry<TransformTargetLabel, List<ITransformer<?>>> entry : transformers.entrySet()) {
                    String className = entry.getKey().getClassName().getClassName();
                    List<ITransformer<?>> transformerList = this.transformersByClass.computeIfAbsent(className, k -> new ArrayList<>());
                    transformerList.addAll(entry.getValue());
                }
            }
            for(List<ITransformer<?>> transformerList : this.transformersByClass.values()) {
                transformerList.sort((t1, t2) -> Comparator.<String>naturalOrder().compare(StringUtils.join(t1.labels(), " "), StringUtils.join(t2.labels(), " ")));
            }
        } catch(ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        this.transformationCache = new ConcurrentHashMap<>();
        try(ObjectInputStream inStream = new ObjectInputStream(new FileInputStream(CLASS_CACHE_DAT))) {
            this.transformationCache = (ConcurrentHashMap<String, Pair<List<byte[]>,byte[]>>)inStream.readObject();
            /* Deduplicate any empty byte arrays to minimize impact of empty classes */
            List<String> keys = new ArrayList<>(this.transformationCache.keySet());
            for(String key : keys) {
                Pair<List<byte[]>,byte[]> pair = this.transformationCache.get(key);
                for(int i = 0; i < pair.getLeft().size(); i++) {
                    if(pair.getLeft().get(i).length == 0) {
                        pair.getLeft().set(i, EMPTY_BYTE_ARRAY);
                    }
                }
                if(pair.getRight().length == 0) {
                    this.transformationCache.put(key, Pair.of(pair.getLeft(), EMPTY_BYTE_ARRAY));
                }
            }
            int size = 0;
            /* Approximate the size in bytes */
            for(Map.Entry<String, Pair<List<byte[]>,byte[]>> entry : this.transformationCache.entrySet()) {
                size += entry.getKey().length();
                size += entry.getValue().getRight().length;
                for(byte[] hash : entry.getValue().getLeft()) {
                    size += hash.length;
                }
            }
            LOGGER.info("Loaded transformer cache, contains " + this.transformationCache.size() + " classes and in-memory size is approximately " + FileUtils.byteCountToDisplaySize(size));
        } catch(IOException | ClassNotFoundException e) {
            LOGGER.error("An error occured while loading transform cache", e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Serializing transform cache to disk");
            try(ObjectOutputStream outStream = new ObjectOutputStream(new FileOutputStream(CLASS_CACHE_DAT))) {
                outStream.writeObject(transformationCache);
            } catch(IOException e) {
                LOGGER.error("An error occured while serializing transform cache", e);
            }
        }, "ModernFix transformer shutdown thread"));
    }

    /**
     * Check the hashed list of transformers and use a cached version of the class if possible. This code needs
     * to be very fast as the entire point is to spend very little time doing transformation work that was done before.
     * @param inputClass The bytecode to be transformed
     * @param className Name of the class
     * @param reason Reason for the class being loaded
     * @return The transformed version of the class
     */
    @Override
    public byte[] transform(byte[] inputClass, String className, String reason) {
        /* We only want to cache actual transformations */
        if(!ITransformerActivity.CLASSLOADING_REASON.equals(reason)) {
            return super.transform(inputClass, className, reason);
        }
        final String internalName = className.replace('.', '/');
        final Type classDesc = Type.getObjectType(internalName);
        final EnumMap<ILaunchPluginService.Phase, List<ILaunchPluginService>> launchPluginTransformerSet = pluginHandler.computeLaunchPluginTransformerSet(classDesc, false, reason, this.auditTrail);
        final boolean needsTransforming = transformStore.needsTransforming(internalName);
        if (!needsTransforming && launchPluginTransformerSet.isEmpty()) {
            return inputClass;
        }
        /* Now compute the hash list for the required transformers */
        ArrayList<byte[]> hashList = new ArrayList<>();
        for(List<ILaunchPluginService> pluginList : launchPluginTransformerSet.values()) {
            pluginList.sort((service1, service2) -> Comparator.<String>naturalOrder().compare(service1.name(), service2.name()));
            for(ILaunchPluginService service : pluginList) {
                byte[] hash = obtainHash(service, className);
                if(hash == null) {
                    return super.transform(inputClass, className, reason);
                }
                hashList.add(hash);
            }
        }
        if(needsTransforming) {
            List<ITransformer<?>> transformers = this.transformersByClass.get(internalName);
            if(transformers != null) {
                for(ITransformer<?> transformer : transformers) {
                    byte[] hash = obtainHash(transformer, className);
                    if(hash == null) {
                        return super.transform(inputClass, className, reason);
                    }
                    hashList.add(hash);
                }
            }
        }
        /* Check if the cache contains a transformed class matching these hashes */
        return transformationCache.compute(className, (name, oldPair) -> {
            boolean hashesMatch = true;
            if(oldPair == null || oldPair.getLeft().size() != hashList.size()) {
                hashesMatch = false;
            } else {
                for(int i = 0; i < oldPair.getLeft().size(); i++) {
                    if(!Arrays.equals(oldPair.getLeft().get(i), hashList.get(i))) {
                        hashesMatch = false;
                    }
                }
            }
            if(hashesMatch)
               return oldPair;
            else {
               if(oldPair != null) {
                   LOGGER.warn("Hashes have changed, discarding cached version of " + name);
               }
               byte[] transformed = super.transform(inputClass, name, reason);
               if(transformed.length == 0)
                   transformed = EMPTY_BYTE_ARRAY; /* deduplicate */
               return Pair.of(hashList, transformed);
            }
        }).getRight();
    }

    private static final byte[] FORGE_HASH = LoadingModList.get().getModFileById("forge").getMods().get(0).getVersion().toString().getBytes(StandardCharsets.UTF_8);

    private byte[] obtainHash(Object o, String className) {
        if(o instanceof CoreModBaseTransformer) {
            return CoreModTransformerHasher.obtainHash((CoreModBaseTransformer<?>)o);
        } else if(o instanceof MixinLaunchPluginLegacy) {
            return MixinTransformerHasher.obtainHash((MixinLaunchPluginLegacy)o, className);
        } else if(o instanceof IHashableTransformer) {
            return ((IHashableTransformer)o).getHashForClass(className);
        } else if(o.getClass().getName().startsWith("net.minecraftforge.")) {
            return FORGE_HASH;
        } else {
            LOGGER.warn("No hash implementation found for: " + o.getClass().getName());
            return null;
        }
    }
}
