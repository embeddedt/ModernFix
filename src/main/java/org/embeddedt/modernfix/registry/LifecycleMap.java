package org.embeddedt.modernfix.registry;

import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

public class LifecycleMap<T> extends Reference2ReferenceOpenHashMap<T, Lifecycle> {
    public LifecycleMap() {
        this.defaultReturnValue(Lifecycle.stable());
    }

    @Override
    public Lifecycle put(T t, Lifecycle lifecycle) {
        if(lifecycle != defRetValue)
            return super.put(t, lifecycle);
        else {
            // need the duplicate containsKey/get logic here to override the default return value
            return super.containsKey(t) ? super.get(t) : null;
        }
    }
}
