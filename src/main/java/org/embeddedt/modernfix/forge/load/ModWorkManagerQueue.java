package org.embeddedt.modernfix.forge.load;

import net.minecraftforge.fml.ModWorkManager;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class ModWorkManagerQueue extends ConcurrentLinkedDeque<Runnable> {

    private static final long PARK_TIME = TimeUnit.MICROSECONDS.toNanos(250);

    /**
     * Sleep for a bit if there are no tasks.
     */
    @Override
    public Runnable pollFirst() {
        Runnable r = super.pollFirst();
        if(r == null) {
            LockSupport.parkNanos(PARK_TIME);
            return null;
        } else {
            return r;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void replace() {
        try {
            Class<?> syncExecutorClass = Class.forName("net.minecraftforge.fml.ModWorkManager$SyncExecutor");
            ConcurrentLinkedDeque<Runnable> taskQueue = (ConcurrentLinkedDeque<Runnable>) ObfuscationReflectionHelper.getPrivateValue((Class)syncExecutorClass, (Object)ModWorkManager.syncExecutor(), "tasks");
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
