package org.embeddedt.modernfix.mixin.perf.parallelize_model_loading.multipart;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.client.renderer.block.model.MultiVariant;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mixin(MultiVariant.class)
public abstract class VariantListMixin {
    @Shadow public abstract List<Variant> getVariants();

    /**
     * @author embeddedt
     * @reason Parallelize calls to getMaterials
     */
    @Overwrite
    public Collection<Material> getMaterials(Function<ResourceLocation, UnbakedModel> pModelGetter, Set<Pair<String, String>> pMissingTextureErrors) {
        List<UnbakedModel> models = this.getVariants().stream().map(Variant::getModelLocation).distinct().map(pModelGetter).collect(Collectors.toList());
        return models.parallelStream().flatMap(model -> model.getMaterials(pModelGetter, pMissingTextureErrors).stream()).collect(Collectors.toSet());
    }
}
