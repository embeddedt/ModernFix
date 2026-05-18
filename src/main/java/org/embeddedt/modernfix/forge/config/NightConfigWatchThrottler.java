package org.embeddedt.modernfix.forge.config;

import com.electronwill.nightconfig.core.file.FileWatcher;
import com.google.common.collect.ForwardingCollection;
import com.google.common.collect.ForwardingMap;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Throttle NightConfig's file watching. There are reports of this consuming excessive CPU time
 * (<a href="https://github.com/TheElectronWill/night-config/pull/144">example</a>) and the spammed iterator calls
 * end up being 10% of allocations when testing in a dev environment.
 */
public class NightConfigWatchThrottler {
    private static final long DELAY = TimeUnit.MILLISECONDS.toNanos(1000);

    @SuppressWarnings("rawtypes")
    public static void throttle() {
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
                                LockSupport.parkNanos(DELAY);
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
