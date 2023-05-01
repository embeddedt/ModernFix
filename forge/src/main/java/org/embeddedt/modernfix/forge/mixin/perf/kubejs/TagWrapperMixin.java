package org.embeddedt.modernfix.forge.mixin.perf.kubejs;

import dev.latvian.kubejs.server.TagEventJS;
import dev.latvian.kubejs.util.UtilsJS;
import me.shedaniel.architectury.registry.Registry;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.embeddedt.modernfix.forge.util.KubeUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Mixin(TagEventJS.TagWrapper.class)
@RequiresMod("kubejs")
public class TagWrapperMixin<T> {
    private String currentPatternStr = null;
    @Inject(method = "add", at = @At(value = "INVOKE", target = "Lme/shedaniel/architectury/registry/Registry;getIds()Ljava/util/Set;", ordinal = 0), locals = LocalCapture.CAPTURE_FAILHARD, remap = false)
    private void saveCurrentPattern(Object ids, CallbackInfoReturnable<TagEventJS.TagWrapper<T>> cir, Iterator<Object> iterator, Object o, String patternStr) {
        currentPatternStr = patternStr;
    }

    @Redirect(method = "add", at = @At(value = "INVOKE", target = "Lme/shedaniel/architectury/registry/Registry;getIds()Ljava/util/Set;", ordinal = 0), remap = false)
    private Set<ResourceLocation> getCachedIds(Registry<T> registryIn) {
        if(currentPatternStr == null)
            throw new AssertionError();
        Set<ResourceLocation> cachedSet = KubeUtil.matchedIdsForRegex.get(currentPatternStr);
        if(cachedSet == null) {
            Pattern thePattern = UtilsJS.parseRegex(currentPatternStr);
            ArrayList<ResourceLocation> locations = new ArrayList<>(registryIn.getIds());
            cachedSet = locations.parallelStream()
                    .filter(rLoc -> thePattern.matcher(rLoc.toString()).find())
                    .collect(Collectors.toSet());
            KubeUtil.matchedIdsForRegex.put(currentPatternStr, cachedSet);
        }
        return cachedSet;
    }

    /**
     * @author embeddedt
     * @reason we handle pattern-matching ourselves to build the cache
     */
    @Redirect(method = "add", at = @At(value = "INVOKE", target = "Ljava/util/regex/Matcher;find()Z"), remap = false)
    private boolean isMatchedStr(Matcher matcher) {
        return true;
    }
}
