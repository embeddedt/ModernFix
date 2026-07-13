package org.embeddedt.modernfix.common.mixin.perf.smart_ingredient_sync;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.embeddedt.modernfix.annotation.ClientOnlyMixin;
import org.embeddedt.modernfix.forge.packet.PacketHandler;
import org.embeddedt.modernfix.forge.recipe.DeferredTagEventHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
@ClientOnlyMixin
public class ClientPacketListenerMixin {
    @Shadow
    @Final
    private Connection connection;

    @Unique
    private boolean modernfix$firstTagSync = true;

    /**
     * @author embeddedt
     * @reason in multiplayer, defer TagsUpdatedEvent and fire it after recipes are sent, so that the observable
     * event order matches vanilla. Mods like TerraFirmaCraft expect TagsUpdatedEvent to fire after RecipesUpdatedEvent
     * on login (and as a result they do not handle reload correctly, but I guess no one tests that).
     */
    @WrapOperation(method = "handleUpdateTags", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private boolean deferTags(IEventBus instance, Event event, Operation<Boolean> original) {
        boolean isLogin = this.modernfix$firstTagSync;
        this.modernfix$firstTagSync = false;
        if (isLogin && !this.connection.isMemoryConnection() && PacketHandler.INGREDIENT_SYNC.isRemotePresent(this.connection) && event instanceof TagsUpdatedEvent t) {
            if (DeferredTagEventHolder.deferredEvent != null) {
                original.call(instance, DeferredTagEventHolder.deferredEvent);
            }
            DeferredTagEventHolder.deferredEvent = t;
            return false;
        } else {
            return original.call(instance, event);
        }
    }

    @Inject(method = "handleUpdateRecipes", at = @At("RETURN"))
    private void fireDeferredTags(ClientboundUpdateRecipesPacket packet, CallbackInfo ci) {
        if (DeferredTagEventHolder.deferredEvent != null) {
            TagsUpdatedEvent deferred = DeferredTagEventHolder.deferredEvent;
            DeferredTagEventHolder.deferredEvent = null;
            MinecraftForge.EVENT_BUS.post(deferred);
        }
    }
}
