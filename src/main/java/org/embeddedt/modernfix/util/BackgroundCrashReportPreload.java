package org.embeddedt.modernfix.util;

import net.minecraft.CrashReport;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Runs {@link CrashReport#preload()} alongside vanilla bootstrap. {@link #join()} must be called once bootstrap is
 * done, so the preload never overlaps mod construction or renderer setup.
 */
public final class BackgroundCrashReportPreload {
    private static FutureTask<Void> task;

    private BackgroundCrashReportPreload() {
    }

    public static void start() {
        FutureTask<Void> preload = new FutureTask<>(CrashReport::preload, null);
        Thread thread = new Thread(preload, "ModernFix crash report preload");
        thread.setDaemon(true);
        task = preload;
        thread.start();
    }

    public static void join() {
        FutureTask<Void> preload = task;
        if (preload == null) {
            return;
        }
        task = null;
        try {
            preload.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            } else if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        }
    }
}
