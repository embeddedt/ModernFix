package org.embeddedt.modernfix.neoforge.mixin.bugfix.blueprint_modif_memory_leak;

import com.google.gson.Gson;
import com.teamabnormals.blueprint.core.util.modification.ObjectModificationManager;
import com.teamabnormals.blueprint.core.util.modification.selection.SelectionSpace;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import org.embeddedt.modernfix.annotation.RequiresMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ObjectModificationManager.class)
@RequiresMod("blueprint")
public abstract class ObjectModificationManagerMixin extends SimpleJsonResourceReloadListener {
    @Shadow(remap = false) protected SelectionSpace selectionSpace;

    public ObjectModificationManagerMixin(Gson gson, String string) {
        super(gson, string);
    }

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("RETURN"), remap = false)
    private void clearSelectionSpace(CallbackInfo ci) {
        this.selectionSpace = consumer -> {};
    }
}
