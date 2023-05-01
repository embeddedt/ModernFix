package org.embeddedt.modernfix.mixin.bugfix.concurrency;

import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderType.CompositeRenderType.class)
@ClientOnlyMixin
public class RenderTypeMixin {
    @Shadow @Final private static ObjectOpenCustomHashSet<RenderType.CompositeRenderType> INSTANCES;

    /**
     * @author embeddedt
     * @reason synchronize, can be accessed by multiple mods during modloading
     */
    @Overwrite
    private static RenderType.CompositeRenderType memoize(String name, VertexFormat format, int drawMode, int bufferSize, boolean useDelegate, boolean needsSorting, RenderType.CompositeState renderState) {
        synchronized (INSTANCES){
            return INSTANCES.addOrGet(new RenderType.CompositeRenderType(name, format, drawMode, bufferSize, useDelegate, needsSorting, renderState));
        }
    }
}
