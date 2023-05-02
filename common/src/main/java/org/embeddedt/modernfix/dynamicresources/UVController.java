package org.embeddedt.modernfix.dynamicresources;

import net.minecraft.client.renderer.block.model.BlockFaceUV;

public class UVController {
    public static final ThreadLocal<Boolean> useDummyUv = ThreadLocal.withInitial(() -> Boolean.FALSE);
    public static final BlockFaceUV dummyUv = new BlockFaceUV(new float[4], 0);
}
