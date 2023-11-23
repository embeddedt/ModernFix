package org.embeddedt.modernfix.forge.mixin.bugfix.file_dialog_title;

import net.minecraft.client.gui.screens.worldselection.WorldGenSettingsComponent;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(WorldGenSettingsComponent.class)
@ClientOnlyMixin
public class WorldGenSettingsComponentMixin {
    /**
     * @author embeddedt
     * @reason Do not provide resource pack-controlled string to TinyFD
     */
    @ModifyArg(method = "*", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/tinyfd/TinyFileDialogs;tinyfd_openFileDialog(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Lorg/lwjgl/PointerBuffer;Ljava/lang/CharSequence;Z)Ljava/lang/String;", remap = false), index = 0)
    private CharSequence sanitizeTitleString(CharSequence original) {
        return "Select settings file (.json)";
    }
}
