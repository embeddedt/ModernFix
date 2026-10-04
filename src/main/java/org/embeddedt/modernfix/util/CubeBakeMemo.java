package org.embeddedt.modernfix.util;

import net.minecraft.client.model.geom.ModelPart;

import java.util.HashSet;
import java.util.Set;

/** Last shared cube and a snapshot of its construction arguments, without boxing on cache hits. */
public final class CubeBakeMemo {
    private final int texCoordU, texCoordV;
    private final int originX, originY, originZ, dimensionX, dimensionY, dimensionZ;
    private final int growX, growY, growZ, texScaleU, texScaleV;
    private final boolean mirror;
    private final Set<?> faces;
    public final ModelPart.Cube cube;

    public CubeBakeMemo(int texCoordU, int texCoordV, float originX, float originY, float originZ,
                        float dimensionX, float dimensionY, float dimensionZ,
                        float growX, float growY, float growZ, boolean mirror,
                        float texScaleU, float texScaleV, Set<?> faces, ModelPart.Cube cube) {
        this.texCoordU = texCoordU;
        this.texCoordV = texCoordV;
        this.mirror = mirror;
        this.originX = Float.floatToIntBits(originX);
        this.originY = Float.floatToIntBits(originY);
        this.originZ = Float.floatToIntBits(originZ);
        this.dimensionX = Float.floatToIntBits(dimensionX);
        this.dimensionY = Float.floatToIntBits(dimensionY);
        this.dimensionZ = Float.floatToIntBits(dimensionZ);
        this.growX = Float.floatToIntBits(growX);
        this.growY = Float.floatToIntBits(growY);
        this.growZ = Float.floatToIntBits(growZ);
        this.texScaleU = Float.floatToIntBits(texScaleU);
        this.texScaleV = Float.floatToIntBits(texScaleV);
        this.faces = new HashSet<>(faces);
        this.cube = cube;
    }

    public boolean matches(int texCoordU, int texCoordV, float originX, float originY, float originZ,
                           float dimensionX, float dimensionY, float dimensionZ,
                           float growX, float growY, float growZ, boolean mirror,
                           float texScaleU, float texScaleV, Set<?> faces) {
        return this.texCoordU == texCoordU
                && this.texCoordV == texCoordV
                && this.mirror == mirror
                && this.originX == Float.floatToIntBits(originX)
                && this.originY == Float.floatToIntBits(originY)
                && this.originZ == Float.floatToIntBits(originZ)
                && this.dimensionX == Float.floatToIntBits(dimensionX)
                && this.dimensionY == Float.floatToIntBits(dimensionY)
                && this.dimensionZ == Float.floatToIntBits(dimensionZ)
                && this.growX == Float.floatToIntBits(growX)
                && this.growY == Float.floatToIntBits(growY)
                && this.growZ == Float.floatToIntBits(growZ)
                && this.texScaleU == Float.floatToIntBits(texScaleU)
                && this.texScaleV == Float.floatToIntBits(texScaleV)
                && faces.equals(this.faces);
    }
}
