package org.embeddedt.modernfix.common.mixin.perf.dynamic_languages;

import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.dynamiclanguages.DynamicLanguageMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableResourceManager.class)
@ClientOnlyMixin
public class ReloadableResourceManagerMixin {
    /**
     * @author embeddedt
     * @reason the current language must stay readable after createReload closes the packs backing it
     */
    @Inject(method = "createReload", at = @At("HEAD"))
    private void materializeCurrentLanguage(CallbackInfoReturnable<ReloadInstance> cir) {
        if ((Object)this == Minecraft.getInstance().getResourceManager()
                && Language.getInstance() instanceof ClientLanguageAccessor language
                && language.getStorage() instanceof DynamicLanguageMap map) {
            map.materialize();
        }
    }
}
