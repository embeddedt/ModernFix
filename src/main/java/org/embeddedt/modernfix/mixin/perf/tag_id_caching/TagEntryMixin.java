package org.embeddedt.modernfix.mixin.perf.tag_id_caching;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TagEntry.class)
public class TagEntryMixin {
    @Shadow @Final private boolean tag;
    @Shadow @Final private ResourceLocation id;
    private ExtraCodecs.TagOrElementLocation cachedLoc;

    /**
     * @author embeddedt
     * @reason use cached location, overwrite rather than inject to avoid allocs
     */
    @Overwrite
    private ExtraCodecs.TagOrElementLocation elementOrTag() {
        ExtraCodecs.TagOrElementLocation loc = cachedLoc;
        if(loc == null) {
            loc = new ExtraCodecs.TagOrElementLocation(this.id, this.tag);
            cachedLoc = loc;
        }
        return loc;
    }
}
