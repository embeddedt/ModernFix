package org.embeddedt.modernfix.util;

import com.google.common.base.Stopwatch;
import org.embeddedt.modernfix.ModernFix;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class AsyncStopwatch {
    private final AtomicLong cpuTimeMs = new AtomicLong(0);
    private final ThreadLocal<Stopwatch> threadStopwatch = ThreadLocal.withInitial(Stopwatch::createUnstarted);

    public void startMeasuringAsync() {
        threadStopwatch.get().start();
    }

    public void stopMeasuringAsync() {
        Stopwatch watch = threadStopwatch.get();
        watch.stop();
        long elapsed = watch.elapsed(TimeUnit.MILLISECONDS);
        cpuTimeMs.addAndGet(elapsed);
        watch.reset();
    }

    public void ensureStoppedAsync() {
        Stopwatch watch = threadStopwatch.get();
        if(watch.isRunning())
            stopMeasuringAsync();
    }

    public long getCpuTime() {
        return cpuTimeMs.get();
    }

    public static void measureAndLogSerialRunningTime(String label, Runnable runnable) {
        ModernFix.LOGGER.info(label + "...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            runnable.run();
            ModernFix.LOGGER.info(label + " took " + stopwatch.elapsed(TimeUnit.MILLISECONDS)/1000f + " seconds");
        } finally {
            stopwatch.stop();
        }
    }
}
