package org.embeddedt.modernfix.mixin.perf.parallelize_model_loading.multipart;

import net.minecraft.client.renderer.model.multipart.Multipart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.stream.Stream;

@Mixin(Multipart.class)
public class MultipartMixin {
    @Redirect(method = "getMaterials", at = @At(value = "INVOKE", target = "Ljava/util/List;stream()Ljava/util/stream/Stream;", ordinal = 0))
    private Stream makeStreamParallel(List instance) {
        return instance.parallelStream();
    }
}
