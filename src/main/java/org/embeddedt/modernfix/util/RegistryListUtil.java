package org.embeddedt.modernfix.util;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

public final class RegistryListUtil {
    private RegistryListUtil() {
    }

    /**
     * Same result as {@link ObjectList#size(int)}, but grows the backing array to a power of two, as
     * {@link ObjectArrayList#size(int)} would otherwise copy it into a slightly larger one on every registration.
     */
    public static void setSizeSmart(ObjectList<?> list, int size) {
        if(list instanceof ObjectArrayList && size > list.size()) {
            int requestedSize = size;
            /* choose next power of two, or this value if it is a power of two */
            int p2Size = Integer.highestOneBit(size);
            if(p2Size != size)
                size = p2Size << 1;
            // grow backing array to power-of-two size, this will return instantly in most cases
            ((ObjectArrayList<?>)list).ensureCapacity(size);
            // write null entries to fill size, to match the behavior of list.size(int)
            while(list.size() < requestedSize)
                list.add(null);
        } else {
            list.size(size);
        }
    }
}
