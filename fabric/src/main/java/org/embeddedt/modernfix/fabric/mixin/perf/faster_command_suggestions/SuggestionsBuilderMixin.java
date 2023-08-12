package org.embeddedt.modernfix.fabric.mixin.perf.faster_command_suggestions;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Simple hack-fix to limit the number of suggestions being processed. Not a perfect fix but mitigates lag decently
 * on an i3-4150.
 */
@Mixin(SuggestionsBuilder.class)
public class SuggestionsBuilderMixin {
    @Unique
    private static final int MAX_SUGGESTIONS = 10000;

    @Shadow(remap = false) @Final @Mutable
    private List<Suggestion> result;

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"), require = 0)
    private <T> boolean addIfFits(List<T> list, T entry) {
        if(list != result || list.size() < MAX_SUGGESTIONS) {
            return list.add(entry);
        }
        return false;
    }
}
