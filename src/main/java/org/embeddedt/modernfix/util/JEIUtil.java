package org.embeddedt.modernfix.util;

import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.Internal;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.runtime.JeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.function.Supplier;

public class JEIUtil {
    private static Supplier<Boolean> isLoading = null;

    public static void registerLoadingRenderer(Supplier<Boolean> isLoading) {
        JEIUtil.isLoading = isLoading;
        MinecraftForge.EVENT_BUS.register(JEIUtil.class);
    }

    @SubscribeEvent
    public static void renderLoad(GuiScreenEvent.DrawScreenEvent.Post event) {
        if(isLoading.get()) {
            Gui.drawString(new PoseStack(), Minecraft.getInstance().font, new TranslatableComponent("modernfix.jei_load"), 0, 0, 0xffffff);
        }
    }
}
