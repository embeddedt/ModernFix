package org.embeddedt.modernfix.util;

import java.util.function.Supplier;

/**
 * An implementation of Supplier that allows separating the time at which the value is computed from when it is
 * retrieved.
 */
public class CachedSupplier<T> implements Supplier<T> {
    private T value = null;

    private boolean hasBeenComputed;
    private final Supplier<T> delegate;

    public CachedSupplier(Supplier<T> delegate) {
        this.delegate = delegate;
    }

    public synchronized void compute() {
        this.value = this.delegate.get();
        this.hasBeenComputed = true;
    }

    @Override
    public synchronized T get() {
        if(this.hasBeenComputed) {
            this.hasBeenComputed = false;
            return this.value;
        } else {
            return this.delegate.get();
        }
    }
}
