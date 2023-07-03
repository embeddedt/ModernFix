package org.embeddedt.modernfix.screen;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.core.config.Option;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OptionList extends ContainerObjectSelectionList<OptionList.Entry> {
    private final int maxNameWidth;

    private static final Component OPTION_ON = new TranslatableComponent("modernfix.option.on").withStyle(style -> style.withColor(ChatFormatting.GREEN));
    private static final Component OPTION_OFF = new TranslatableComponent("modernfix.option.off").withStyle(style -> style.withColor(ChatFormatting.RED));

    private static final Set<String> OPTIONS_MISSING_HELP = new HashSet<>();

    private ModernFixConfigScreen mainScreen;


    public OptionList(ModernFixConfigScreen arg, Minecraft arg2) {
        super(arg2,arg.width + 45, arg.height, 43, arg.height - 32, 20);

        this.mainScreen = arg;

        int maxW = 0;
        Map<String, Option> optionMap = ModernFixMixinPlugin.instance.config.getOptionMap();
        List<String> sortedKeys = optionMap.keySet().stream().filter(key -> {
            int dotCount = 0;
            for(char c : key.toCharArray()) {
                if(c == '.')
                    dotCount++;
            }
            return dotCount >= 2;
        }).sorted().collect(Collectors.toList());
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
        private final Button helpButton;
        private final Option option;

        public OptionEntry(String optionName, Option option) {
            this.name = optionName;
            this.option = option;
            this.toggleButton = new Button(0, 0, 55, 20, new TextComponent(""), (arg) -> {
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
            });
            this.helpButton = new Button(75, 0, 20, 20, new TextComponent("?"), (arg) -> {
                Minecraft.getInstance().setScreen(new ModernFixOptionInfoScreen(mainScreen, optionName));
            });
            if(!I18n.exists("modernfix.option." + optionName)) {
                this.helpButton.active = false;
                if(ModernFixPlatformHooks.isDevEnv() && OPTIONS_MISSING_HELP.add(optionName))
                    ModernFix.LOGGER.warn("Missing help for {}", optionName);
            }
        }

        @Override
        public void render(PoseStack matrixStack, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTicks) {
            MutableComponent nameComponent = new TextComponent(this.name);
            if(this.option.isUserDefined())
                nameComponent = nameComponent.withStyle(ChatFormatting.ITALIC).append(new TranslatableComponent("modernfix.config.not_default"));
            OptionList.this.minecraft.font.draw(matrixStack, nameComponent, (float)(left + 160 - OptionList.this.maxNameWidth), (float)(top + height / 2 - 4), 16777215);
            this.toggleButton.x = left + 175;
            this.toggleButton.y = top;
            this.toggleButton.setMessage(getOptionMessage(this.option));
            this.toggleButton.render(matrixStack, mouseX, mouseY, partialTicks);
            this.helpButton.x = left + 175 + 55;
            this.helpButton.y = top;
            this.helpButton.render(matrixStack, mouseX, mouseY, partialTicks);
        }

        private Component getOptionMessage(Option option) {
            return option.isEnabled() ? OPTION_ON : OPTION_OFF;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.toggleButton, this.helpButton);
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            for(GuiEventListener listener : children()) {
                if(listener.mouseClicked(mouseX, mouseY, button))
                    return true;
            }
            return false;
        }

        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            for(GuiEventListener listener : children()) {
                if(listener.mouseReleased(mouseX, mouseY, button))
                    return true;
            }
            return false;
        }
    }

    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        public Entry() {
        }
    }
}
