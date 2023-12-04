package org.embeddedt.modernfix.forge.util;

import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

public class ModUtil {
    private static final Set<Class<?>> erroredContexts = new HashSet<>();

    private static final ClassLoader targetClassLoader = Thread.currentThread().getContextClassLoader();

    private static class ModernFixForkJoinWorkerThread extends ForkJoinWorkerThread {
        ModernFixForkJoinWorkerThread(ForkJoinPool pool) {
            super(pool);
            /* Ensure that the context class loader is set correctly */
            this.setContextClassLoader(targetClassLoader);
        }
    }

    public static ForkJoinPool commonPool = new ForkJoinPool(
            ForkJoinPool.getCommonPoolParallelism(),
            ModernFixForkJoinWorkerThread::new,
            null,
            false
    );
}
