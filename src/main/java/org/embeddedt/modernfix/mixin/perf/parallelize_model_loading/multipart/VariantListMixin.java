package org.embeddedt.modernfix.mixin.perf.parallelize_model_loading.multipart;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.renderer.model.IUnbakedModel;
import net.minecraft.client.renderer.model.RenderMaterial;
import net.minecraft.client.renderer.model.Variant;
import net.minecraft.client.renderer.model.VariantList;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mixin(VariantList.class)
public abstract class VariantListMixin {
    @Shadow public abstract List<Variant> getVariants();

    /**
     * @author embeddedt
     * @reason Parallelize calls to getMaterials
     */
    @Overwrite
    public Collection<RenderMaterial> getMaterials(Function<ResourceLocation, IUnbakedModel> pModelGetter, Set<Pair<String, String>> pMissingTextureErrors) {
        List<IUnbakedModel> models = this.getVariants().stream().map(Variant::getModelLocation).distinct().map(pModelGetter).collect(Collectors.toList());
        return models.parallelStream().flatMap(model -> model.getMaterials(pModelGetter, pMissingTextureErrors).stream()).collect(Collectors.toSet());
    }
}
