package org.embeddedt.modernfix.mixin.perf.kubejs;

import dev.latvian.kubejs.recipe.RecipeJS;
import dev.latvian.kubejs.recipe.filter.IDFilter;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.util.KubeUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(IDFilter.class)
@RequiresMod("kubejs")
public class IDFilterMixin {
    @Shadow @Final private ResourceLocation id;
    private RecipeJS _target;
    private boolean _targetSearched = false;

    /**
     * @author embeddedt
     * @reason avoid scanning every recipe
     */
    @Overwrite(remap = false)
    public boolean test(RecipeJS recipe) {
        if(!_targetSearched) {
            _target = KubeUtil.originalRecipesByHash.get(this.id);
            _targetSearched = true;
        }
        return recipe == _target;
    }
}
