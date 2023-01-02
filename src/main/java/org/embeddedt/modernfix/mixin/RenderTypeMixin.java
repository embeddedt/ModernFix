package org.embeddedt.modernfix.mixin;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collections;

@Mixin(targets = {"net/minecraft/client/renderer/RenderType$Type"})
public class RenderTypeMixin {
    @Shadow @Final private static ObjectOpenCustomHashSet<RenderType.Type> TYPES;

    /**
     * @author embeddedt
     * @reason synchronize, can be accessed by multiple mods during modloading
     */
    @Overwrite
    private static RenderType.Type getOrCreate(String name, VertexFormat format, int drawMode, int bufferSize, boolean useDelegate, boolean needsSorting, RenderType.State renderState) {
        synchronized (TYPES){
            return TYPES.addOrGet(new RenderType.Type(name, format, drawMode, bufferSize, useDelegate, needsSorting, renderState));
        }
    }
}
