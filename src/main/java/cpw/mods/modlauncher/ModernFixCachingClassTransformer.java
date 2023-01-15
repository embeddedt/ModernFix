package cpw.mods.modlauncher;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.Files;
import cpw.mods.modlauncher.api.ITransformerActivity;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.lang.model.SourceVersion;

public class ModernFixCachingClassTransformer extends ClassTransformer {
    private static final Logger LOGGER = LogManager.getLogger("ModernFixCachingTransformer");
    private Map<String, Optional<byte[]>> cache = new ConcurrentHashMap<>();
    private final static int QUEUE_SIZE = 512; // Config.recentCacheSize;
    Optional<Cache<String, byte[]>> recentCache = QUEUE_SIZE < 0 ? Optional.empty() :
            Optional.of(CacheBuilder.newBuilder().maximumSize(QUEUE_SIZE).build());

    private static final boolean FORCE_REBUILD_CACHE = Boolean.parseBoolean(System.getProperty("coretweaks.transformerCache.full.forceRebuild", "false"));

    public static final boolean DEBUG_PRINT = true;

    private int lastSaveSize = 0;
    private BlockingQueue<String> dirtyClasses = new LinkedBlockingQueue<String>();
    private SaveThread saveThread = new SaveThread(this);

    private static final File CLASS_CACHE_DAT = childFile(FMLPaths.GAMEDIR.get().resolve("modernfix").resolve("classTransformerFull.cache").toFile());
    private static final File CLASS_CACHE_DAT_ERRORED = childFile(FMLPaths.GAMEDIR.get().resolve("modernfix").resolve("classTransformerFull.cache.errored").toFile());
    private static final File CLASS_CACHE_DAT_TMP = childFile(FMLPaths.GAMEDIR.get().resolve("modernfix").resolve("classTransformerFull.cache~").toFile());

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

    static class SaveThread extends Thread {

        private ModernFixCachingClassTransformer cacheTransformer;

        private int saveInterval = 10000;

        public SaveThread(ModernFixCachingClassTransformer ct) {
            this.cacheTransformer = ct;
            setName("CacheTransformer save thread");
            setDaemon(false);
        }

        @Override
        public void run() {
            while(true) {
                try {
                    Thread.sleep(saveInterval);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                cacheTransformer.doSave();
            }
        }
    }

    public ModernFixCachingClassTransformer(TransformStore transformStore, LaunchPluginHandler pluginHandler, TransformingClassLoader transformingClassLoader, TransformerAuditTrail trail) {
        super(transformStore, pluginHandler, transformingClassLoader, trail);

        if(FORCE_REBUILD_CACHE) {// || Persistence.modsChanged()) {
            clearCache(FORCE_REBUILD_CACHE ? "forceRebuild JVM flag was set." : "mods have changed.");
        } else {
            loadCache();
        }
        saveThread.start();
    }

    private void clearCache(String reason) {
        LOGGER.info("Rebuilding class cache, because " + reason);
        CLASS_CACHE_DAT.delete();
    }

    public void doSave() {
        saveCache();
    }

    private void loadCache() {
        File inFile = CLASS_CACHE_DAT;

        if(inFile.exists()) {
            LOGGER.info("Loading class cache.");
            cache.clear();

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(inFile)))){
                try {
                    while(true) { // EOFException should break the loop
                        String className = in.readUTF();
                        int classLength = in.readInt();
                        byte[] classData = new byte[classLength];
                        int bytesRead = in.read(classData, 0, classLength);

                        if(!isValidClassName(className)) {
                            throw new RuntimeException("Invalid class name: " + className);
                        } else if(bytesRead != classLength) {
                            throw new RuntimeException("Length of " + className + " doesn't match advertised length of " + classLength);
                        } else {
                            cache.put(className, Optional.of(classData));

                            superDebug("Loaded " + className);
                        }
                    }
                } catch(EOFException eof) {}
            } catch (Exception e) {
                LOGGER.error("There was an error reading the transformer cache. A new one will be created. The previous one has been saved as " + CLASS_CACHE_DAT_ERRORED.getName() + " for inspection.");
                CLASS_CACHE_DAT.renameTo(CLASS_CACHE_DAT_ERRORED);
                e.printStackTrace();
                cache.clear();
            }
            LOGGER.info("Loaded " + cache.size() + " cached classes.");

            lastSaveSize = cache.size();
        } else {
            LOGGER.info("Couldn't find class cache file");
        }
    }

    private void saveCacheFully() {
        File outFile = CLASS_CACHE_DAT;
        File outFileTmp = CLASS_CACHE_DAT_TMP;

        LOGGER.info("Performing full save of class cache (size: " + cache.size() + ")");
        saveCacheChunk(cache.keySet(), outFileTmp, false);

        try {
            Files.move(outFileTmp, outFile);
        } catch (IOException e) {
            LOGGER.error("Failed to finish saving class cache");
            e.printStackTrace();
        }
    }

    private void saveCache() {
        if(dirtyClasses.isEmpty()) {
            return; // don't save if the cache hasn't changed
        }

        File outFile = CLASS_CACHE_DAT;
        try {
            outFile.createNewFile();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        List<String> classesToSave = new ArrayList<String>();
        dirtyClasses.drainTo(classesToSave);

        if(DEBUG_PRINT) {
            LOGGER.info("Saving class cache (size: " + lastSaveSize + " -> " + cache.size() + " | +" + classesToSave.size() + ")");
        }
        saveCacheChunk(classesToSave, outFile, true);

        lastSaveSize += classesToSave.size();
    }

    private void saveCacheChunk(Collection<String> classesToSave, File outFile, boolean append) {
        try(DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outFile, append)))){
            for(String name : classesToSave) {
                Optional<byte[]> data = cache.get(name);
                if(data != null && data.isPresent()) {
                    out.writeUTF(name);
                    out.writeInt(data.get().length);
                    out.write(data.get());
                }
            }
            if(DEBUG_PRINT) {
                LOGGER.info("Saved class cache");
            }
        } catch (IOException e) {
            LOGGER.info("Exception saving class cache");
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private String describeBytecode(byte[] basicClass) {
        return basicClass == null ? "null" : String.format("length: %d, hash: %x", basicClass.length, basicClass.hashCode());
    }

    @Override
    public byte[] transform(byte[] basicClass, String transformedName, String reason) {
        /* We only want to cache actual transformations */
        if(!ITransformerActivity.CLASSLOADING_REASON.equals(reason) || basicClass.length == 0) {
            return super.transform(basicClass, transformedName, reason);
        }
        byte[] result = null;
        String name = transformedName;

        try {
            boolean dontCache = false;
            /*
            for(String badPrefix : badClasses) {
                if(transformedName.startsWith(badPrefix)){
                    dontCache = true;
                    break;
                }
            }
             */

            if(cache.containsKey(transformedName) && !dontCache) {
                if(cache.get(transformedName).isPresent()) { // we still remember it
                    result = cache.get(transformedName).get();

                    if(recentCache.isPresent()) {
                        // classes are only loaded once, so no need to keep it around in RAM
                        cache.put(transformedName, Optional.empty());

                        // but keep it around in case it's needed again by another transformer in the chain
                        recentCache.get().put(transformedName, result);
                    }
                } else if(recentCache.isPresent()){ // we have forgotten it, hopefully it's still around in the recent queue
                    result = recentCache.get().getIfPresent(transformedName);
                    if(result == null) {
                        LOGGER.warn("Couldn't find " + transformedName + " in cache. Is recent queue too small? (" + QUEUE_SIZE + ")");
                    }
                }
            }
            if(result == null){
                basicClass = super.transform(basicClass, transformedName, reason);

                if(basicClass != null && !dontCache) {
                    cache.put(transformedName, Optional.of(basicClass)); // then cache it
                    dirtyClasses.add(transformedName);
                }
                result = basicClass;
            }
            if(result != null && recentCache.isPresent() && !dontCache) {
                recentCache.get().put(transformedName, result);
            }
        } catch(Exception e) {
            throw e; // pass it to LaunchClassLoader, who will handle it
        } finally {
            //wrappedTransformers.alt = this;
        }
        return result;
    }

    private void superDebug(String msg) {
        if(DEBUG_PRINT) {
            LOGGER.debug(msg);
        }
    }
}
