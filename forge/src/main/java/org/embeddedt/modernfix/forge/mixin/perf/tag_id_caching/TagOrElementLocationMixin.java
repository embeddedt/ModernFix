package org.embeddedt.modernfix.forge.mixin.perf.tag_id_caching;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ExtraCodecs.TagOrElementLocation.class)
public class TagOrElementLocationMixin {
    @Shadow @Final private boolean tag;
    @Shadow @Final private ResourceLocation id;
    private String cachedDecoratedId;

    /**
     * @author embeddedt
     * @reason use cached ID, overwrite rather than inject to avoid allocs
     */
    @Overwrite
    private String decoratedId() {
        String id = cachedDecoratedId;
        if(id == null) {
            id = this.tag ? "#" + this.id : this.id.toString();
            cachedDecoratedId = id;
        }
        return id;
    }
}
