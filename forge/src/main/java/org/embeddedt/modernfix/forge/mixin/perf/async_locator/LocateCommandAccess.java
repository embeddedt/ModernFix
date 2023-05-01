package org.embeddedt.modernfix.forge.mixin.perf.async_locator;

import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.server.commands.LocateCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocateCommand.class)
public interface LocateCommandAccess {
	@Accessor("ERROR_FAILED")
	static SimpleCommandExceptionType getErrorFailed() {
		throw new UnsupportedOperationException();
	}
}
