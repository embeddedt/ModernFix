package org.embeddedt.modernfix.common.mixin.feature.disable_unihex_font;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.font.CodepointMap;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import net.minecraft.server.packs.resources.ResourceManager;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.lang.reflect.Constructor;

@Mixin(UnihexProvider.Definition.class)
@ClientOnlyMixin
public class UnihexProviderDefinitionMixin {
    @Inject(method = "unpack", at = @At("HEAD"), cancellable = true)
    private void disableProvider(CallbackInfoReturnable<Either<GlyphProviderDefinition.Loader, GlyphProviderDefinition.Reference>> cir) {
        cir.setReturnValue(Either.left(this::mfix$loadEmpty));
    }

    private GlyphProvider mfix$loadEmpty(ResourceManager resourceManager) throws IOException {
        try {
            ModernFix.LOGGER.warn("Unihex provider is disabled, a number of Unicode characters will likely not render");
            Constructor<UnihexProvider> constructor = UnihexProvider.class.getDeclaredConstructor(CodepointMap.class);
            constructor.setAccessible(true);
            return constructor.newInstance(new CodepointMap<>(Object[]::new, Object[][]::new));
        } catch(ReflectiveOperationException e) {
            throw new IOException("Failed to create empty loader", e);
        }
    }
}
