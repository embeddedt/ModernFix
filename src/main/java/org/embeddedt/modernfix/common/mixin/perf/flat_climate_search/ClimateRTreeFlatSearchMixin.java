package org.embeddedt.modernfix.common.mixin.perf.flat_climate_search;

import net.minecraft.world.level.biome.Climate;
import org.embeddedt.modernfix.annotation.FeatureLevel;
import org.embeddedt.modernfix.annotation.RequiresFeatureLevel;
import org.embeddedt.modernfix.duck.IFlatClimateLeaf;
import org.embeddedt.modernfix.duck.IFlatClimateTree;
import org.embeddedt.modernfix.world.gen.FlatClimateTree;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Climate.RTree.class)
@RequiresFeatureLevel(FeatureLevel.BETA)
public class ClimateRTreeFlatSearchMixin<T> implements IFlatClimateTree<T> {
    @Shadow @Final private Climate.RTree.Node<T> root;
    @Shadow @Final private ThreadLocal<Climate.RTree.Leaf<T>> lastResult;

    @Unique
    private FlatClimateTree<T> mfix$flatTree;

    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("RETURN"))
    private void buildFlatTree(CallbackInfo ci) {
        this.mfix$flatTree = FlatClimateTree.build(this.root,
                node -> ((ClimateRTreeNodeAccessor) node).mfix$getParameterSpace(),
                subTree -> (Climate.RTree.Node<T>[]) ((ClimateRTreeSubTreeAccessor) (Object) subTree).mfix$getChildren(),
                (leaf, index) -> ((IFlatClimateLeaf) (Object) leaf).mfix$setFlatIndex(index));
    }

    @Override
    public boolean mfix$hasFlatTree() {
        return this.mfix$flatTree != null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T mfix$findValueFlat(Climate.TargetPoint target) {
        Climate.RTree.Leaf<T> last = this.lastResult.get();
        int lastIndex = last != null ? ((IFlatClimateLeaf) (Object) last).mfix$getFlatIndex() : -1;
        Climate.RTree.Leaf<T> leaf = this.mfix$flatTree.search(target, last, lastIndex);
        if (leaf != last) {
            this.lastResult.set(leaf);
        }
        return (T) ((ClimateRTreeLeafAccessor) (Object) leaf).mfix$getValue();
    }
}
