package org.embeddedt.modernfix.mixin.perf.parallelize_model_loading;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(BlockModelShaper.class)
public class BlockModelShaperMixin {
    private static Map<BlockState, String> stateToPropertiesCache = new ConcurrentHashMap<>();

    @Redirect(method = "stateToModelLocation(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/resources/model/ModelResourceLocation;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockModelShaper;statePropertiesToString(Ljava/util/Map;)Ljava/lang/String;"))
    private static String getCachedProperty(Map<Property<?>, Comparable<?>> values, ResourceLocation location, BlockState state) {
        /* We intentionally don't use the values parameter inside the lambda to avoid an allocation */
        return stateToPropertiesCache.computeIfAbsent(state, s -> BlockModelShaper.statePropertiesToString(s.getValues()));
    }
}
