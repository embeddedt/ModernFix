package org.embeddedt.modernfix.common.mixin.perf.worldgen_allocation;

import com.mojang.datafixers.util.Pair;
import net.minecraft.world.level.biome.Climate;
import org.embeddedt.modernfix.annotation.FeatureLevel;
import org.embeddedt.modernfix.annotation.RequiresFeatureLevel;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Defer construction of the RTree until the first biome generation, since it can take a bit on low-end systems.
 */
@Mixin(Climate.ParameterList.class)
@RequiresFeatureLevel(FeatureLevel.BETA)
public class ClimateParameterListMixin<T> {

    @Shadow
    @Final
    @Mutable
    private Climate.RTree<T> index;

    @Shadow
    @Final
    private List<Pair<Climate.ParameterPoint, T>> values;

    @Unique
    private volatile boolean mfix$initialized;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Climate$RTree;create(Ljava/util/List;)Lnet/minecraft/world/level/biome/Climate$RTree;"))
    private Climate.RTree<T> deferCreation(List<Pair<Climate.ParameterPoint, T>> nodes) {
        return null;
    }

    @Redirect(method = "findValueIndex(Lnet/minecraft/world/level/biome/Climate$TargetPoint;Lnet/minecraft/world/level/biome/Climate$DistanceMetric;)Ljava/lang/Object;", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/biome/Climate$ParameterList;index:Lnet/minecraft/world/level/biome/Climate$RTree;", opcode = Opcodes.GETFIELD))
    private Climate.RTree<T> getTreeLazy(Climate.ParameterList<T> instance) {
        if (!this.mfix$initialized) {
            synchronized (this) {
                if (!this.mfix$initialized) {
                    this.index = Climate.RTree.create(this.values);
                    this.mfix$initialized = true;
                }
            }
        }
        return this.index;
    }
}
