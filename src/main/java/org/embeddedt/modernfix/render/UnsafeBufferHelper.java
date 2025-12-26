package org.embeddedt.modernfix.render;

import org.embeddedt.modernfix.ModernFix;
import org.lwjgl.system.MemoryUtil;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/**
 * Helper that frees ByteBuffers allocated by BufferBuilders, and nulls out the address pointer
 * to prevent double frees.
 *
 * @author Moulberry
 */
public class UnsafeBufferHelper {
    private static final MemoryUtil.MemoryAllocator ALLOCATOR = MemoryUtil.getAllocator(false);

    private static sun.misc.Unsafe UNSAFE = null;
    private static long ADDRESS = -1;

    static {
        try {
            final Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe)theUnsafe.get(null);

            final Field addressField = MemoryUtil.class.getDeclaredField("ADDRESS");
            addressField.setAccessible(true);
            ADDRESS = addressField.getLong(null);
        } catch(Throwable t) {
            ModernFix.LOGGER.error("Could load unsafe/buffer address", t);
        }
    }

    public static void init() {

    }

    public static void free(ByteBuffer buf) {
        if(UNSAFE != null && ADDRESS >= 0) {
            // set the address to 0 to prevent double free
            long address = UNSAFE.getAndSetLong(buf, ADDRESS, 0);
            if(address != 0) {
                ALLOCATOR.free(address);
            }
        } else {
            ALLOCATOR.free(MemoryUtil.memAddress0(buf));
        }
    }
}
