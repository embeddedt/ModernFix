package org.embeddedt.modernfix.util;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

public class DirectExecutorService extends AbstractExecutorService {
    private boolean isShutdown;

    @Override
    public void shutdown() {
        isShutdown = true;
    }

    @NotNull
    @Override
    public List<Runnable> shutdownNow() {
        isShutdown = true;
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return isShutdown;
    }

    @Override
    public boolean isTerminated() {
        return isShutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        return true;
    }

    @Override
    public void execute(@NotNull Runnable command) {
        command.run();
    }
}
