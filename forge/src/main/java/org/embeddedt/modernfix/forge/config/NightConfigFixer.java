package org.embeddedt.modernfix.forge.config;

import com.electronwill.nightconfig.core.file.FileWatcher;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.util.CommonModUtil;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class NightConfigFixer {
    public static final LinkedHashSet<Runnable> configsToReload = new LinkedHashSet<>();
    public static void monitorFileWatcher() {
        if(!ModernFixMixinPlugin.instance.isOptionEnabled("bugfix.fix_config_crashes.NightConfigFixerMixin"))
            return;
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

        /**
         * Add the config
         */
        @Override
        public void run() {
            synchronized(configsToReload) {
                int oldSize = configsToReload.size();
                configsToReload.add(configTracker);
                if(oldSize == 0) {
                    ModernFixMixinPlugin.instance.logger.info("Config file change detected on disk. The Forge feature to watch config files for changes is currently disabled due to random corruption issues.");
                    ModernFixMixinPlugin.instance.logger.info("This functionality will be restored in a future ModernFix update.");
                }
            }
        }
    }
}
