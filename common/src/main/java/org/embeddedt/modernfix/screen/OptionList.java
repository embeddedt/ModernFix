package org.embeddedt.modernfix.screen;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.core.config.Option;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OptionList extends ContainerObjectSelectionList<OptionList.Entry> {
    private final int maxNameWidth;

    private static final Component OPTION_ON = Component.translatable("modernfix.option.on").withStyle(style -> style.withColor(ChatFormatting.GREEN));
    private static final Component OPTION_OFF = Component.translatable("modernfix.option.off").withStyle(style -> style.withColor(ChatFormatting.RED));

    private ModernFixConfigScreen mainScreen;


    public OptionList(ModernFixConfigScreen arg, Minecraft arg2) {
        super(arg2,arg.width + 45, arg.height, 43, arg.height - 32, 20);

        this.mainScreen = arg;

        int maxW = 0;
        Map<String, Option> optionMap = ModernFixMixinPlugin.instance.config.getOptionMap();
        List<String> sortedKeys = optionMap.keySet().stream().filter(key -> !key.equals("mixin.core")).sorted().collect(Collectors.toList());
        for(String key : sortedKeys) {
            Option option = optionMap.get(key);
            int w = this.minecraft.font.width(key);
            maxW = Math.max(w, maxW);
            this.addEntry(new OptionEntry(key, option));
        }
        this.maxNameWidth = maxW;
    }

    protected int getScrollbarPosition() {
        return super.getScrollbarPosition() + 15 + 20;
    }

    public int getRowWidth() {
        return super.getRowWidth() + 32;
    }

    class OptionEntry extends Entry {
        private final String name;

        private final Button toggleButton;
        private final Option option;

        public OptionEntry(String optionName, Option option) {
            this.name = optionName;
            this.option = option;
            this.toggleButton = new Button.Builder(Component.literal(""), (arg) -> {
                this.option.setEnabled(!this.option.isEnabled(), !this.option.isUserDefined());
                try {
                    ModernFixMixinPlugin.instance.config.save();
                    if(!OptionList.this.mainScreen.madeChanges) {
                        OptionList.this.mainScreen.madeChanges = true;
                    }
                } catch(IOException e) {
                    // revert
                    this.option.setEnabled(!this.option.isEnabled(), !this.option.isUserDefined());
                    ModernFix.LOGGER.error("Unable to save config", e);
                }
            }).pos(0, 0).size(95, 20).build();
        }

        @Override
        public void render(PoseStack matrixStack, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTicks) {
            MutableComponent nameComponent = Component.literal(this.name);
            if(this.option.isUserDefined())
                nameComponent = nameComponent.withStyle(ChatFormatting.ITALIC).append(Component.translatable("modernfix.config.not_default"));
            OptionList.this.minecraft.font.draw(matrixStack, nameComponent, (float)(left + 160 - OptionList.this.maxNameWidth), (float)(top + height / 2 - 4), 16777215);
            this.toggleButton.setPosition(left + 175, top);
            this.toggleButton.setMessage(getOptionMessage(this.option));
            this.toggleButton.render(matrixStack, mouseX, mouseY, partialTicks);
        }

        private Component getOptionMessage(Option option) {
            return option.isEnabled() ? OPTION_ON : OPTION_OFF;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.toggleButton);
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return this.toggleButton.mouseClicked(mouseX, mouseY, button);
        }

        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return this.toggleButton.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return Collections.emptyList();
        }
    }

    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        public Entry() {
        }
    }
}
