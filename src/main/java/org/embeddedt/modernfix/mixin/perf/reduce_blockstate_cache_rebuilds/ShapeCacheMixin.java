package org.embeddedt.modernfix.mixin.perf.reduce_blockstate_cache_rebuilds;

import com.refinedmods.refinedstorage.block.shape.ShapeCache;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.embeddedt.modernfix.ModernFix;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ShapeCache.class)
public class ShapeCacheMixin {
    @Final
    @Mutable
    @Shadow(remap = false) private static Map<BlockState, VoxelShape> CACHE;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void useConcurrentMap(CallbackInfo ci) {
        CACHE = new ConcurrentHashMap<>();
        ModernFix.LOGGER.info("Successfully replaced ShapeCache with concurrent map");
    }
}
