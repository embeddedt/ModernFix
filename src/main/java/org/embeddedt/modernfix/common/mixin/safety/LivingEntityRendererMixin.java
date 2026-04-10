package org.embeddedt.modernfix.common.mixin.safety;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LivingEntityRenderer.class)
@ClientOnlyMixin
public abstract class LivingEntityRendererMixin<T extends Entity, M extends EntityModel<T>> {
    @Shadow
    public abstract boolean addLayer(RenderLayer<T, M> layer);

    /**
     * @author embeddedt
     * @reason avoid CMEs from buggy mods calling addLayer on wrong thread
     */
    @WrapMethod(method = "addLayer")
    private boolean handleOffThreadLayerAdd(RenderLayer<T, M> layer, Operation<Boolean> original) {
        if (!Minecraft.getInstance().isSameThread()) {
            ModernFix.LOGGER.error("LivingEntityRenderer.addLayer called on wrong thread", new Exception());
            Minecraft.getInstance().tell(() -> this.addLayer(layer));
            return true;
        }
        return original.call(layer);
    }
}
