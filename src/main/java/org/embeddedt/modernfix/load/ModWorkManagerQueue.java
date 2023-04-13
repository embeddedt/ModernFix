package org.embeddedt.modernfix.load;

import net.minecraftforge.fml.ModWorkManager;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class ModWorkManagerQueue extends ConcurrentLinkedDeque<Runnable> {
    private static final long PARK_TIME = TimeUnit.MILLISECONDS.toNanos(25);

    private static final Runnable DUMMY_TASK = () -> {};

    private boolean shouldReturnDummyTask = false;

    /**
     * Sleep for a bit if there are no tasks.
     */
    @Override
    public Runnable pollFirst() {
        Runnable r = super.pollFirst();
        if(r == null) {
            LockSupport.parkNanos(PARK_TIME);
            boolean isReturning = shouldReturnDummyTask;
            shouldReturnDummyTask = !shouldReturnDummyTask;
            /*
             * We need to kick FML to redraw the loading screen periodically,
             * but also allow actually exiting the executor loop, so that
             * loading can complete if async work is done.
             *
             * This is accomplished by alternating between returning a dummy
             * task and nothing.
             */
            return isReturning ? DUMMY_TASK : null;
        } else {
            return r;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void replace() {
        try {
            Class<?> syncExecutorClass = Class.forName("net.minecraftforge.fml.ModWorkManager$SyncExecutor");
            ConcurrentLinkedDeque<Runnable> taskQueue = (ConcurrentLinkedDeque<Runnable>)ObfuscationReflectionHelper.getPrivateValue((Class)syncExecutorClass, (Object)ModWorkManager.syncExecutor(), "tasks");
            ModWorkManagerQueue q = new ModWorkManagerQueue();
            Runnable task;
            do {
                task = taskQueue.pollFirst();
                if(task != null)
                    q.push(task);
            } while(task != null);
            ObfuscationReflectionHelper.setPrivateValue((Class)syncExecutorClass, (Object)ModWorkManager.syncExecutor(), q, "tasks");
        } catch(ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }
}
