package org.embeddedt.modernfix.mixin.perf.model_optimizations;

import com.mojang.math.Transformation;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Objects;

@Mixin(Transformation.class)
public class TransformationMatrixMixin {
    @Shadow @Final private Matrix4f matrix;
    private Integer cachedHashCode = null;
    /**
     * @author embeddedt
     * @reason use cached hashcode if exists
     */
    @Overwrite(remap = false)
    public int hashCode() {
        int hash;
        if(cachedHashCode != null) {
            hash = cachedHashCode;
        } else {
            hash = Objects.hashCode(this.matrix);
            cachedHashCode = hash;
        }
        return hash;
    }
}
