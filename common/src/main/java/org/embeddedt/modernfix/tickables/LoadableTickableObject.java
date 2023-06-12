package org.embeddedt.modernfix.tickables;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class LoadableTickableObject<T> implements TickableObject {
    private volatile int ticksInactive = 0;
    private final int timeout;
    private final Supplier<T> loader;
    private final Consumer<T> finalizer;
    private volatile T theObject = null;

    public LoadableTickableObject(int timeout, Supplier<T> loader, Consumer<T> finalizer) {
        this(timeout, loader, finalizer, null);
    }

    public LoadableTickableObject(int timeout, Supplier<T> loader, Consumer<T> finalizer, @Nullable T initialValue) {
        this.timeout = timeout;
        this.loader = loader;
        this.finalizer = finalizer;
        this.theObject = initialValue;
    }

    public T get() {
        synchronized (this) {
            ticksInactive++;
            T obj = theObject;
            if(obj == null) {
                obj = loader.get();
                theObject = obj;
            }
            return obj;
        }
    }

    public final void tick() {
        synchronized (this) {
            ticksInactive++;
            if(ticksInactive >= this.timeout) {
                finalizer.accept(theObject);
                theObject = null;
            }
        }
    }
}
