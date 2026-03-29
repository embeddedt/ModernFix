package org.embeddedt.modernfix.util;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Like {@link Executors#newSingleThreadExecutor()}, but handles the case where the background executor schedules
 * a task to itself and waits for it the way a direct executor would.
 */
public class SingleThreadedWorkerService extends AbstractExecutorService {
    private final AtomicReference<Thread> thread = new AtomicReference<>();
    private final ExecutorService executorService;

    public SingleThreadedWorkerService() {
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Worker-Main");
            t.setPriority(Thread.MIN_PRIORITY);
            thread.set(t);
            return t;
        });
    }

    @Override
    public void shutdown() {
        executorService.shutdown();
    }

    @NotNull
    @Override
    public List<Runnable> shutdownNow() {
        return executorService.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return executorService.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return executorService.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        return executorService.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(@NotNull Runnable command) {
        if (Thread.currentThread() == thread.get()) {
            command.run();
        } else {
            executorService.execute(command);
        }
    }
}
