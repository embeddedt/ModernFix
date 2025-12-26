package org.embeddedt.modernfix.common.mixin.perf.model_optimizations;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.renderer.block.model.MultiVariant;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.function.Function;

@Mixin(value = MultiVariant.class, priority = 700)
@ClientOnlyMixin
public abstract class MultiVariantMixin {
    @Shadow public abstract List<Variant> getVariants();

    /**
     * @author embeddedt
     * @reason avoid streams, try to optimize for common case
     */
    @Overwrite
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter) {
        var variants = this.getVariants();
        // There is usually only a single variant
        if (variants.size() == 1) {
            modelGetter.apply(variants.get(0).getModelLocation()).resolveParents(modelGetter);
        } else if(variants.size() > 1) {
            ObjectOpenHashSet<ResourceLocation> seenLocations = new ObjectOpenHashSet<>(variants.size());
            for (var variant : variants) {
                var location = variant.getModelLocation();
                if (seenLocations.add(location)) {
                    modelGetter.apply(location).resolveParents(modelGetter);
                }
            }
        }
    }
}
