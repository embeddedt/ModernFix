package org.embeddedt.modernfix.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ModernFixConfigScreen extends Screen {
    private OptionList optionList;
    private Screen lastScreen;

    public boolean madeChanges = false;
    private Button doneButton;
    public ModernFixConfigScreen(Screen lastScreen) {
        super(Component.translatable("modernfix.config"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        this.optionList = new OptionList(this, this.minecraft);
        this.addWidget(this.optionList);
        this.doneButton = new Button(this.width / 2 - 100, this.height - 29, 200, 20, CommonComponents.GUI_DONE, (arg) -> {
            this.minecraft.setScreen(lastScreen);
        });
        this.addRenderableWidget(this.doneButton);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        this.optionList.render(poseStack, mouseX, mouseY, partialTicks);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 8, 16777215);
        this.doneButton.setMessage(madeChanges ? Component.translatable("modernfix.config.done_restart") : CommonComponents.GUI_DONE);
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }
}
