package org.embeddedt.modernfix.forge.dynresources;

import net.minecraft.client.renderer.RenderType;

import java.util.function.Predicate;

public class ModernFixCTMPredicate implements Predicate<RenderType> {
    public Predicate<RenderType> ctmOverride;
    public Predicate<RenderType> defaultPredicate = type -> type == RenderType.solid();

    @Override
    public boolean test(RenderType renderType) {
        Predicate<RenderType> override = ctmOverride;
        return override != null ? override.test(renderType) : defaultPredicate.test(renderType);
    }
}
