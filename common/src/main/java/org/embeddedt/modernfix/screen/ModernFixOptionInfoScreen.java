package org.embeddedt.modernfix.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class ModernFixOptionInfoScreen extends Screen {
    private final Screen lastScreen;
    private final Component description;

    public ModernFixOptionInfoScreen(Screen lastScreen, String optionName) {
        super(Component.literal(optionName));

        this.lastScreen = lastScreen;
        this.description = Component.translatable("modernfix.option." + optionName);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(new Button.Builder(CommonComponents.GUI_DONE, (button) -> {
            this.onClose();
        }).pos(this.width / 2 - 100, this.height - 29).size(200, 20).build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(lastScreen);
    }

    private void drawMultilineString(GuiGraphics guiGraphics, Font fr, Component str, int x, int y) {
        for(FormattedCharSequence s : fr.split(str, this.width - 50)) {
            guiGraphics.drawString(fr, s, x, y, 16777215, true);
            y += fr.lineHeight;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 16777215);
        this.drawMultilineString(guiGraphics, this.minecraft.font, description, 10, 50);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }
}
