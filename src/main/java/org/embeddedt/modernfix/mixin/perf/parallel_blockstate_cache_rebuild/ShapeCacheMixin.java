package org.embeddedt.modernfix.mixin.perf.parallel_blockstate_cache_rebuild;

import com.refinedmods.refinedstorage.block.shape.ShapeCache;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.shapes.VoxelShape;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ShapeCache.class)
public class ShapeCacheMixin {
    @Shadow private static Map<BlockState, VoxelShape> CACHE;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void useConcurrentMap(CallbackInfo ci) {
        CACHE = new ConcurrentHashMap<>();
        ModernFix.LOGGER.info("Successfully replaced ShapeCache with concurrent map");
    }
}
