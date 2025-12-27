package org.embeddedt.modernfix.util;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class NamedPreparableResourceListener implements PreparableReloadListener {
    private final PreparableReloadListener delegate;
    public NamedPreparableResourceListener(PreparableReloadListener delegate) {
        this.delegate = delegate;
    }


    @Override
    public CompletableFuture<Void> reload(SharedState sharedState, Executor exectutor, PreparationBarrier barrier, Executor applyExectutor) {
        return this.delegate.reload(sharedState, exectutor, barrier, applyExectutor);
    }

    @Override
    public void prepareSharedState(SharedState sharedState) {
        this.delegate.prepareSharedState(sharedState);
    }

    @Override
    public String getName() {
        return this.delegate.getName() + " [" + this.delegate.getClass().getName() + "]";
    }
}
