package org.embeddedt.modernfix.common.mixin.bugfix.buffer_builder_leak;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.render.UnsafeBufferHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(BufferBuilder.class)
public class BufferBuilderMixin {
    @Shadow private ByteBuffer buffer;

    private static boolean leakReported = false;

    /**
     * Ensure UnsafeBufferHelper is classloaded early, to avoid Forge's event transformer showing an error in the log.
     */
    @Inject(method = "<clinit>", at = @At(value = "RETURN"))
    private static void initUnsafeBufferHelper(CallbackInfo ci) {
        UnsafeBufferHelper.init();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            ByteBuffer buf = buffer;
            // can be null if a mod already tried to free the buffer
            if(buf != null) {
                if(!leakReported) {
                    leakReported = true;
                    ModernFix.LOGGER.warn("One or more BufferBuilders have been leaked, ModernFix will attempt to correct this.");
                }
                UnsafeBufferHelper.free(buf);
                buffer = null;
            }
        } finally {
            super.finalize();
        }
    }
}
