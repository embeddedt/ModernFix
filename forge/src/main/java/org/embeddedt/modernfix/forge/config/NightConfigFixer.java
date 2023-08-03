package org.embeddedt.modernfix.forge.config;

import com.electronwill.nightconfig.core.file.FileWatcher;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.util.CommonModUtil;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class NightConfigFixer {
    public static final LinkedHashSet<Runnable> configsToReload = new LinkedHashSet<>();
    private static int tickCounter = 0;
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

    /**
     * Called by the render thread on the client, and the server thread on the server. Processes all the accumulated
     * file watch events.
     */
    public static void runReloads() {
        if((tickCounter++ % 20) != 0)
            return;
        List<Runnable> runnablesToRun;
        synchronized (configsToReload) {
            if(configsToReload.isEmpty())
                return;
            runnablesToRun = new ArrayList<>(configsToReload);
            configsToReload.clear();
        }
        for(Runnable r : runnablesToRun) {
            try {
                r.run();
            } catch(RuntimeException e) {
                e.printStackTrace();
            }
        }
        ModernFix.LOGGER.info("Processed {} config reloads", runnablesToRun.size());
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
         * Add the config runnable to the list to be processed by the main thread.
         */
        @Override
        public void run() {
            synchronized(configsToReload) {
                configsToReload.add(configTracker);
            }
        }
    }
}
