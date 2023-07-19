package org.embeddedt.modernfix.forge.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.file.FileWatcher;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.util.CommonModUtil;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Relatively simple patch to wait for config saving to finish, made complex by Night Config classes being package-private,
 * and Forge not allowing mixins into libraries.
 */
public class NightConfigFixer {
    public static void monitorFileWatcher() {
        CommonModUtil.runWithoutCrash(() -> {
            FileWatcher watcher = FileWatcher.defaultInstance();
            Field field = FileWatcher.class.getDeclaredField("watchedFiles");
            field.setAccessible(true);
            ConcurrentHashMap<Path, ?> theMap = (ConcurrentHashMap<Path, ?>)field.get(watcher);
            // replace the backing map of watched files with one we control
            field.set(watcher, new MonitoringMap(theMap));
            ModernFixMixinPlugin.instance.logger.info("Applied Forge config corruption patch");
        }, "replacing Night Config watchedFiles map");
    }

    private static final Class<?> WATCHED_FILE = LamdbaExceptionUtils.uncheck(() -> Class.forName("com.electronwill.nightconfig.core.file.FileWatcher$WatchedFile"));
    private static final Field CHANGE_HANDLER = ObfuscationReflectionHelper.findField(WATCHED_FILE, "changeHandler");

    private static final Class<?> WRITE_SYNC_CONFIG = LamdbaExceptionUtils.uncheck(() -> Class.forName("com.electronwill.nightconfig.core.file.WriteSyncFileConfig"));
    private static final Class<?> AUTOSAVE_CONFIG = LamdbaExceptionUtils.uncheck(() -> Class.forName("com.electronwill.nightconfig.core.file.AutosaveCommentedFileConfig"));
    private static final Field AUTOSAVE_FILECONFIG = ObfuscationReflectionHelper.findField(AUTOSAVE_CONFIG, "fileConfig");

    private static final Field CURRENTLY_WRITING = ObfuscationReflectionHelper.findField(WRITE_SYNC_CONFIG, "currentlyWriting");

    private static final Map<Class<?>, Field> CONFIG_WATCHER_TO_CONFIG_FIELD = Collections.synchronizedMap(new HashMap<>());

    private static Field getCurrentlyWritingFieldOnWatcher(Object watcher) {
        return CONFIG_WATCHER_TO_CONFIG_FIELD.computeIfAbsent(watcher.getClass(), clz -> {
            while(clz != null && clz != Object.class) {
                for(Field f : clz.getDeclaredFields()) {
                    if(CommentedFileConfig.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        ModernFixMixinPlugin.instance.logger.debug("Found CommentedFileConfig: field '{}' on {}", f.getName(), clz.getName());
                        return f;
                    }
                }
                clz = clz.getSuperclass();
            }
            return null;
        });
    }

    static class MonitoringMap extends ConcurrentHashMap<Path, Object> {
        public MonitoringMap(ConcurrentHashMap<Path, ?> oldMap) {
            super(oldMap);
        }

        @Override
        public Object computeIfAbsent(Path key, Function<? super Path, ?> mappingFunction) {
            return super.computeIfAbsent(key, path -> {
                Object watchedFile = mappingFunction.apply(path);
                try {
                    Runnable changeHandler = (Runnable)CHANGE_HANDLER.get(watchedFile);
                    CHANGE_HANDLER.set(watchedFile, new MonitoringConfigTracker(changeHandler));
                } catch(ReflectiveOperationException e) {
                    e.printStackTrace();
                }
                return watchedFile;
            });
        }
    }

    static class MonitoringConfigTracker implements Runnable {
        private final Runnable configTracker;

        MonitoringConfigTracker(Runnable r) {
            this.configTracker = r;
        }

        private static final Set<Class<?>> UNKNOWN_FILE_CONFIG_CLASSES = Collections.synchronizedSet(new ReferenceOpenHashSet<>());

        private void protectFromSaving(FileConfig config, Runnable runnable) throws ReflectiveOperationException {
            if(WRITE_SYNC_CONFIG.isAssignableFrom(config.getClass())) {
                // keep trying to write, releasing the config lock each time in case something else needs to lock it
                // for any reason
                while(true) {
                    // acquiring synchronized block here should in theory prevent any other concurrent loads/saves, based
                    // off WriteSyncFileConfig implementation
                    synchronized (config) {
                        if(CURRENTLY_WRITING.getBoolean(config)) {
                            ModernFixMixinPlugin.instance.logger.fatal("Config being written during load!!!");
                            try { Thread.sleep(500); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
                            continue;
                        }
                        // at this point, currentlyWriting is false, and we acquired synchronized lock, should be good to
                        // go
                        runnable.run();
                        break;
                    }
                }
            } else if(AUTOSAVE_CONFIG.isAssignableFrom(config.getClass())) {
                FileConfig fc = (FileConfig)AUTOSAVE_FILECONFIG.get(config);
                protectFromSaving(fc, runnable);
            } else {
                if(UNKNOWN_FILE_CONFIG_CLASSES.add(config.getClass()))
                    ModernFixMixinPlugin.instance.logger.warn("Unexpected FileConfig class: {}", config.getClass().getName());
                runnable.run();
            }
        }

        /**
         * This entrypoint runs when the file watcher has detected a change on the config file. Before passing
         * this through to Forge, use reflection hacks to confirm the config system is not still writing to the file.
         * If it is, spin until writing finishes. Immediately returning might result in the event never being observed.
         */
        @Override
        public void run() {
            try {
                Field theField = getCurrentlyWritingFieldOnWatcher(this.configTracker);
                if(theField != null) {
                    CommentedFileConfig cfg = (CommentedFileConfig)theField.get(this.configTracker);
                    // will synchronize and check saving flag
                    protectFromSaving(cfg, configTracker);
                }
            } catch(ReflectiveOperationException e) {
                e.printStackTrace();
            }
        }
    }
}
