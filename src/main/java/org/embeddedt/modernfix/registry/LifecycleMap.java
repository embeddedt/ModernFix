package org.embeddedt.modernfix.registry;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;

public class LifecycleMap<T> extends Reference2ReferenceOpenHashMap<ResourceKey<T>, RegistrationInfo> {
    public LifecycleMap() {
        this.defaultReturnValue(RegistrationInfo.BUILT_IN);
    }

    @Override
    public RegistrationInfo put(ResourceKey<T> t, RegistrationInfo lifecycle) {
        if(lifecycle != defRetValue)
            return super.put(t, lifecycle);
        else {
            // need the duplicate containsKey/get logic here to override the default return value
            return super.containsKey(t) ? super.get(t) : null;
        }
    }
}
