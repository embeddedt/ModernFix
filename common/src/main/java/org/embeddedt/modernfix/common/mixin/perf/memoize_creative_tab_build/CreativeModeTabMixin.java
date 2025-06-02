package org.embeddedt.modernfix.common.mixin.perf.memoize_creative_tab_build;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabMixin {
    @Shadow public abstract CreativeModeTab.Type getType();

    @Unique
    private CreativeModeTab.ItemDisplayParameters mfix$oldParameters;

    @Unique
    private static boolean MFIX$REBUILT_NON_CATEGORY = false;

    /**
     * @author embeddedt
     * @reason Vanilla already does similar memoization in the CreativeModeTabs class, but mods often have to bypass
     * it due to wanting to build the tab contents before search trees are populated. By memoizing at this lower level,
     * we can skip the expensive work of computing the tab contents multiple times while not affecting the logic in
     * CreativeModeTabs.
     */
    @WrapMethod(method = "buildContents")
    private synchronized void buildContentsIfChanged(CreativeModeTab.ItemDisplayParameters parameters, Operation<Void> original) {
        synchronized (CreativeModeTab.class) {
            if (mfix$oldParameters == null || mfix$oldParameters.needsUpdate(parameters.enabledFeatures(), parameters.hasPermissions(), parameters.holders())) {
                original.call(parameters);
                if (this.getType() == CreativeModeTab.Type.CATEGORY) {
                    if (MFIX$REBUILT_NON_CATEGORY) {
                        // We must mark every other tab that's not a category as needing rebuild. Not doing this causes mods
                        // that build a search tab early on without having built the dependencies to permanently leave it
                        // in a broken state.
                        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
                            if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
                                ((CreativeModeTabMixin)(Object)tab).mfix$oldParameters = null;
                            }
                        }
                        MFIX$REBUILT_NON_CATEGORY = false;
                    }
                } else {
                    MFIX$REBUILT_NON_CATEGORY = true;
                }
            }
            mfix$oldParameters = parameters;
        }
    }
}
