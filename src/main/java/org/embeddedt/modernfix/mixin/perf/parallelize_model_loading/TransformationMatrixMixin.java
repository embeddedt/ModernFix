package org.embeddedt.modernfix.mixin.perf.parallelize_model_loading;

import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.TransformationMatrix;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Objects;

@Mixin(TransformationMatrix.class)
public class TransformationMatrixMixin {
    @Shadow @Final private Matrix4f matrix;
    private Integer cachedHashCode = null;
    /**
     * @author embeddedt
     * @reason use cached hashcode if exists
     */
    @Overwrite
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
