package org.embeddedt.modernfix.mixin.perf.kubejs;

import dev.latvian.kubejs.server.TagEventJS;
import dev.latvian.kubejs.util.ConsoleJS;
import dev.latvian.kubejs.util.ListJS;
import dev.latvian.kubejs.util.UtilsJS;
import it.unimi.dsi.fastutil.chars.CharOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.shedaniel.architectury.registry.Registry;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.modernfix.util.KubeUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
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
public class TagWrapperMixin<T> {
    private static final CharOpenHashSet REGEX_SPECIAL_CHARS = new CharOpenHashSet(new char[] {
            '.', '+', '*','?','^','$','(',')','[',']','{','}','|','\\', '/'
    });

    /**
     * @author embeddedt
     * @reason only iterate over the whole registry if a regex is given, otherwise use the given registry name as-is
     */
    @Redirect(method = "add", at = @At(value = "INVOKE", target = "Ldev/latvian/kubejs/util/UtilsJS;parseRegex(Ljava/lang/Object;)Ljava/util/regex/Pattern;"), remap = false)
    private Pattern skipRegex(Object o) {
        String inputStr = (String)o;
        boolean regexCharFound = false;
        for(int i = 0; i < inputStr.length(); i++) {
            if(REGEX_SPECIAL_CHARS.contains(inputStr.charAt(i))) {
                regexCharFound = true;
                break;
            }
        }
        if(regexCharFound)
            return UtilsJS.parseRegex(inputStr);
        else
            return null;
    }

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
