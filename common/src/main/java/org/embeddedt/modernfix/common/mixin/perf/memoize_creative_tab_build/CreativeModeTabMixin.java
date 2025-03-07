package org.embeddedt.modernfix.common.mixin.perf.memoize_creative_tab_build;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CreativeModeTab.class)
public class CreativeModeTabMixin {
    @Unique
    private CreativeModeTab.ItemDisplayParameters mfix$oldParameters;

    /**
     * @author embeddedt
     * @reason Vanilla already does similar memoization in the CreativeModeTabs class, but mods often have to bypass
     * it due to wanting to build the tab contents before search trees are populated. By memoizing at this lower level,
     * we can skip the expensive work of computing the tab contents multiple times while not affecting the logic in
     * CreativeModeTabs.
     */
    @WrapMethod(method = "buildContents")
    private synchronized void buildContentsIfChanged(CreativeModeTab.ItemDisplayParameters parameters, Operation<Void> original) {
        if (mfix$oldParameters == null || mfix$oldParameters.needsUpdate(parameters.enabledFeatures(), parameters.hasPermissions(), parameters.holders())) {
            original.call(parameters);
        }
        mfix$oldParameters = parameters;
    }
}
