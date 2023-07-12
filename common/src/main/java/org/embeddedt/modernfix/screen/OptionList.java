package org.embeddedt.modernfix.screen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.*;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.core.ModernFixMixinPlugin;
import org.embeddedt.modernfix.core.config.Option;
import org.embeddedt.modernfix.core.config.OptionCategories;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class OptionList extends ContainerObjectSelectionList<OptionList.Entry> {
    private final int maxNameWidth;

    private static final Component OPTION_ON = new TranslatableComponent("modernfix.option.on").withStyle(style -> style.withColor(ChatFormatting.GREEN));
    private static final Component OPTION_OFF = new TranslatableComponent("modernfix.option.off").withStyle(style -> style.withColor(ChatFormatting.RED));

    private static final Set<String> OPTIONS_MISSING_HELP = new HashSet<>();

    private ModernFixConfigScreen mainScreen;

    private static MutableComponent getOptionComponent(String optionName) {
        String friendlyKey = "modernfix.option.name." + optionName;
        TextComponent baseComponent = new TextComponent(optionName);
        if(I18n.exists(friendlyKey))
            return new TranslatableComponent(friendlyKey).withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, baseComponent)));
        else
            return baseComponent;
    }


    public OptionList(ModernFixConfigScreen arg, Minecraft arg2) {
        super(arg2,arg.width + 45, arg.height, 43, arg.height - 32, 20);

        this.mainScreen = arg;

        int maxW = 0;
        Multimap<String, Option> optionsByCategory = ModernFixMixinPlugin.instance.config.getOptionCategoryMap();
        List<String> theCategories = OptionCategories.getCategoriesInOrder();
        for(String category : theCategories) {
            String categoryTranslationKey = "modernfix.option.category." + category;
            this.addEntry(new CategoryEntry(new TranslatableComponent(categoryTranslationKey)
                    .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TranslatableComponent(categoryTranslationKey + ".description"))))
            ));
            List<Option> sortedKeys = optionsByCategory.get(category).stream().filter(key -> {
                int dotCount = 0;
                for(char c : key.getName().toCharArray()) {
                    if(c == '.')
                        dotCount++;
                }
                return dotCount >= 2;
            }).sorted(Comparator.comparing(Option::getName)).collect(Collectors.toList());
            for(Option option : sortedKeys) {
                int w = this.minecraft.font.width(getOptionComponent(option.getName()));
                maxW = Math.max(w, maxW);
                this.addEntry(new OptionEntry(option.getName(), option));
            }
        }
        this.maxNameWidth = maxW;
    }

    protected int getScrollbarPosition() {
        return super.getScrollbarPosition() + 15 + 20;
    }

    public int getRowWidth() {
        return super.getRowWidth() + 32;
    }

    class CategoryEntry extends Entry {
        private final Component name;
        private final int width;

        public CategoryEntry(Component component) {
            this.name = component;
            this.width = OptionList.this.minecraft.font.width(this.name);
        }

        public void render(PoseStack matrixStack, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTicks) {
            Font var10000 = OptionList.this.minecraft.font;
            float x = (float)(OptionList.this.minecraft.screen.width / 2 - this.width / 2);
            int y = top + height - 10;
            var10000.draw(matrixStack, this.name, x, y, 16777215);
            if(mouseX >= x && mouseY >= y && mouseX <= (x + this.width) && mouseY <= (y + OptionList.this.minecraft.font.lineHeight))
                OptionList.this.mainScreen.renderComponentHoverEffect(matrixStack, this.name.getStyle(), mouseX, mouseY);
        }

        public boolean changeFocus(boolean focus) {
            return false;
        }

        public List<? extends GuiEventListener> children() {
            return Collections.emptyList();
        }
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
            }, (btn, gfx, x, y) -> {
                if(this.option.isModDefined()) {
                    String disablingMods = String.join(", ", this.option.getDefiningMods());
                    OptionList.this.mainScreen.renderTooltip(
                            gfx,
                            new TranslatableComponent("modernfix.option." + (this.option.isEnabled() ? "enabled" : "disabled"))
                                    .append(new TranslatableComponent("modernfix.option.mod_override", disablingMods)),
                            x,
                            y
                    );
                }
            });
            if(this.option.isModDefined())
                this.toggleButton.active = false;
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
            MutableComponent nameComponent = getOptionComponent(this.name);
            if(this.option.isUserDefined())
                nameComponent = nameComponent.withStyle(style -> style.withItalic(true)).append(new TranslatableComponent("modernfix.config.not_default"));
            float textX = (float)(left + 160 - OptionList.this.maxNameWidth);
            float textY = (float)(top + height / 2 - 4);
            OptionList.this.minecraft.font.draw(matrixStack, nameComponent, textX, textY, 16777215);
            this.toggleButton.x = left + 175;
            this.toggleButton.y = top;
            this.toggleButton.setMessage(getOptionMessage(this.option));
            this.toggleButton.render(matrixStack, mouseX, mouseY, partialTicks);
            this.helpButton.x = left + 175 + 55;
            this.helpButton.y = top;
            this.helpButton.render(matrixStack, mouseX, mouseY, partialTicks);
            if(mouseX >= textX && mouseY >= textY && mouseX <= (textX + OptionList.this.maxNameWidth) && mouseY <= (textY + OptionList.this.minecraft.font.lineHeight))
                OptionList.this.mainScreen.renderComponentHoverEffect(matrixStack, nameComponent.getStyle(), mouseX, mouseY);
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
