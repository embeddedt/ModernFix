package org.embeddedt.modernfix.common.mixin.perf.dynamic_languages;

import net.minecraft.client.resources.language.ClientLanguage;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ClientLanguage.class)
@ClientOnlyMixin
public interface ClientLanguageAccessor {
    @Accessor
    Map<String, String> getStorage();
}
