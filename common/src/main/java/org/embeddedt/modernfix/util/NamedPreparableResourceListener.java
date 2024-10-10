package org.embeddedt.modernfix.util;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class NamedPreparableResourceListener implements PreparableReloadListener {
    private final PreparableReloadListener delegate;
    public NamedPreparableResourceListener(PreparableReloadListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier stage, ResourceManager resourceManager, Executor backgroundExecutor, Executor gameExecutor) {
        return this.delegate.reload(stage, resourceManager, backgroundExecutor, gameExecutor);
    }

    @Override
    public String getName() {
        return this.delegate.getName() + " [" + this.delegate.getClass().getName() + "]";
    }
}
