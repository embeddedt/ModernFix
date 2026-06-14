package org.embeddedt.modernfix.forge.config;

import com.electronwill.nightconfig.core.file.FileWatcher;
import com.google.common.collect.ForwardingCollection;
import com.google.common.collect.ForwardingMap;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Throttle NightConfig's file watching. There are reports of this consuming excessive CPU time
 * (<a href="https://github.com/TheElectronWill/night-config/pull/144">example</a>) and the spammed iterator calls
 * end up being 10% of allocations when testing in a dev environment.
 */
public class NightConfigWatchThrottler {
    private static final long DELAY = TimeUnit.MILLISECONDS.toNanos(1000);
    
    private static final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    // FIXED: Add shutdown hook to clean up watcher threads
    private static void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            isShuttingDown.set(true);
        }, "ModernFix-ShutdownHook"));
    }
    
    @SuppressWarnings("rawtypes")
    public static void throttle() {
        // FIXED: Register shutdown hook for clean cleanup
        addShutdownHook();
        Map watchedDirs = ObfuscationReflectionHelper.getPrivateValue(FileWatcher.class, FileWatcher.defaultInstance(), "watchedDirs");
        Thread launchThread = Thread.currentThread();
        Map watchedDirsWrapper = new ForwardingMap() {
            @Override
            protected Map delegate() {
                return watchedDirs;
            }

            private Collection cachedValues;

            @Override
            public Collection values() {
                if(cachedValues == null) {
                    Collection values = super.values();
                    cachedValues = new ForwardingCollection() {
                        @Override
                        protected Collection delegate() {
                            return values;
                        }

                        @Override
                        public Iterator iterator() {
                            // iterator() is called at the beginning of each iteration of the watch loop,
                            // so it is a good spot to inject the delay.
                            if (Thread.currentThread() != launchThread) {
                                // FIXED: Check for shutdown state to prevent new watches from being created
                                if (isShuttingDown.get()) {
                                    return java.util.Collections.emptyIterator();
                                }
                                LockSupport.parkNanos(DELAY);
                                // FIXED: Properly handle thread interruption to allow graceful container shutdown
                                if (Thread.currentThread().isInterrupted()) {
                                    return java.util.Collections.emptyIterator();
                                }
                            }
                            return super.iterator();
                        }
                    };
                }
                return cachedValues;
            }
        };
        // Force all classes related to the iterator to be loaded ahead of time. This is necessary to prevent
        // a ConcurrentModificationException from being thrown inside ModLauncher when the NightConfig file
        // watcher thread loads forwarding collection classes while the main thread is still mutating the
        // launch plugin map.
        //noinspection StatementWithEmptyBody
        for (var ignored : watchedDirsWrapper.values()) {

        }
        ObfuscationReflectionHelper.setPrivateValue(FileWatcher.class, FileWatcher.defaultInstance(), watchedDirsWrapper, "watchedDirs");
    }
}
