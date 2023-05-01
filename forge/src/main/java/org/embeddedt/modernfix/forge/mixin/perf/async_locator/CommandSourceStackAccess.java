package org.embeddedt.modernfix.forge.mixin.perf.async_locator;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandSourceStack.class)
public interface CommandSourceStackAccess {
	@Accessor
	CommandSource getSource();
}
