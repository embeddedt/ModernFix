package org.embeddedt.modernfix.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.*;
import org.jetbrains.annotations.Nullable;

public class ModernFixConfigScreen extends Screen {
    private OptionList optionList;
    private Screen lastScreen;

    public boolean madeChanges = false;
    private Button doneButton, wikiButton;
    private double lastScrollAmount = 0;

    public ModernFixConfigScreen(Screen lastScreen) {
        super(new TranslatableComponent("modernfix.config"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        this.optionList = new OptionList(this, this.minecraft);
        this.optionList.setScrollAmount(lastScrollAmount);
        this.addWidget(this.optionList);
        this.wikiButton = new Button(this.width / 2 - 155, this.height - 29, 150, 20, new TranslatableComponent("modernfix.config.wiki"), (arg) -> {
            Util.getPlatform().openUri("https://github.com/embeddedt/ModernFix/wiki/Summary-of-Patches");
        });
        this.doneButton = new Button(this.width / 2 - 155 + 160, this.height - 29, 150, 20, CommonComponents.GUI_DONE, (arg) -> {
            this.onClose();
        });
        this.addRenderableWidget(this.wikiButton);
        this.addRenderableWidget(this.doneButton);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(lastScreen);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        this.optionList.render(poseStack, mouseX, mouseY, partialTicks);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 8, 16777215);
        this.doneButton.setMessage(madeChanges ? new TranslatableComponent("modernfix.config.done_restart") : CommonComponents.GUI_DONE);
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    @Override
    public void renderComponentHoverEffect(PoseStack matrixStack, @Nullable Style style, int mouseX, int mouseY) {
        super.renderComponentHoverEffect(matrixStack, style, mouseX, mouseY);
    }

    public void setLastScrollAmount(double d) {
        this.lastScrollAmount = d;
    }
}
