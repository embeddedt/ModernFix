package cpw.mods.modlauncher;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

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

    private final File CLASS_CACHE_FOLDER = childFile(FMLPaths.GAMEDIR.get().resolve("modernfix").resolve("classCacheV1").toFile());
    private final LaunchPluginHandler pluginHandler;
    private final TransformStore transformStore;
    private final TransformerAuditTrail auditTrail;
    private final TransformingClassLoader transformingClassLoader;
    private final HashMap<String, List<ITransformer<?>>> transformersByClass;

    private ConcurrentHashMap<String, Pair<List<byte[]>, byte[]>> transformationCache;
    private ForkJoinPool classSaverPool = ForkJoinPool.commonPool();

    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private static File childFile(File file) {
        file.getParentFile().mkdirs();
        return file;
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
        /* TODO maybe sanitize the class name? */
        File cacheLocation = new File(CLASS_CACHE_FOLDER, className.replace('.', '/'));
        boolean hashesMatch = true;
        try(ObjectInputStream stream = new ObjectInputStream(new FileInputStream(cacheLocation))) {
            ArrayList<byte[]> savedHash = (ArrayList<byte[]>)stream.readObject();
            for(int i = 0; i < savedHash.size(); i++) {
                if(!Arrays.equals(savedHash.get(i), hashList.get(i))) {
                    hashesMatch = false;
                    break;
                }
            }
            if(hashesMatch)
                inputClass = (byte[])stream.readObject();
        } catch(IOException | ClassNotFoundException | ClassCastException e) {
            if(!(e instanceof FileNotFoundException))
                e.printStackTrace();
            hashesMatch = false;
        }
        if(!hashesMatch) {
            inputClass = super.transform(inputClass, className, reason);
            final byte[] classToSave = inputClass;
            classSaverPool.submit(() -> {
                cacheLocation.getParentFile().mkdirs();
                try(ObjectOutputStream stream = new ObjectOutputStream(new FileOutputStream(cacheLocation))) {
                    stream.writeObject(hashList);
                    stream.writeObject(classToSave);
                } catch(IOException e) {
                    e.printStackTrace();
                }
            });
        }
        return inputClass;
    }

    private final byte[] FORGE_HASH = LoadingModList.get().getModFileById("forge").getMods().get(0).getVersion().toString().getBytes(StandardCharsets.UTF_8);

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
