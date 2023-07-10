package org.embeddedt.modernfix.fabric.modfixs;

import net.minecraft.client.KeyMapping;
import org.embeddedt.modernfix.util.CommonModUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class DecorativeBlocksFix {
    public static void fix() {
        CommonModUtil.runWithoutCrash(() -> {
            Class<?> decorativeBlocksSetup = Class.forName("lilypuree.decorative_blocks.client.ClientSetup");
            Field keybindField = decorativeBlocksSetup.getDeclaredField("switchItemState");
            keybindField.setAccessible(true);
            KeyMapping keybinding = (KeyMapping)keybindField.get(null);
            Class<?> keyBindHelper = Class.forName("net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper");
            Method keyBindRegister = keyBindHelper.getDeclaredMethod("registerKeyBinding", KeyMapping.class);
            keyBindRegister.setAccessible(true);
            keyBindRegister.invoke(null, keybinding);
        }, "registering Decorative Blocks keybind to allow configuration");
    }
}
