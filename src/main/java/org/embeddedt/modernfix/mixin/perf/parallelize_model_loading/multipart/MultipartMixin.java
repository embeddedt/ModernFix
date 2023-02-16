package org.embeddedt.modernfix.mixin.perf.parallelize_model_loading.multipart;

import net.minecraft.client.renderer.block.model.multipart.MultiPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.stream.Stream;

@Mixin(MultiPart.class)
public class MultipartMixin {
    @Redirect(method = "getMaterials", at = @At(value = "INVOKE", target = "Ljava/util/List;stream()Ljava/util/stream/Stream;", ordinal = 0))
    private Stream makeStreamParallel(List instance) {
        return instance.parallelStream();
    }
}
