package org.embeddedt.modernfix.common.mixin.perf.dynamic_sounds;

import com.mojang.blaze3d.audio.SoundBuffer;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynamicresources.DynamicSoundHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

@ClientOnlyMixin
@Mixin(SoundBuffer.class)
public class SoundBufferMixin implements DynamicSoundHelpers.SoundBufAccess {
    @Unique
    private long mfix$durationNanos;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void computeDuration(ByteBuffer data, AudioFormat format, CallbackInfo ci) {
        if (data != null) {
            int numFrames = data.capacity() / format.getFrameSize();
            double seconds = ((double)numFrames / format.getFrameRate());
            mfix$durationNanos = Math.max(0, (long)Math.ceil(seconds * 1_000_000_000.0));
        } else {
            mfix$durationNanos = 0;
        }
    }

    @Override
    public long mfix$getDurationNanos() {
        return mfix$durationNanos;
    }
}
