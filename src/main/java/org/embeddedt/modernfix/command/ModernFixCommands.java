package org.embeddedt.modernfix.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import org.embeddedt.modernfix.duck.IProfilingServerFunctionManager;

import static net.minecraft.commands.Commands.literal;

public class ModernFixCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("modernfix")
                .then(literal("mcfunctions").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            if(level == null) {
                                context.getSource().sendFailure(Component.literal("Couldn't find server level"));
                                return 0;
                            }
                            if (level.getServer().getFunctions() instanceof IProfilingServerFunctionManager profiler) {
                                context.getSource().sendSuccess(() -> Component.literal("mcfunction runtime breakdown:"), false);
                                for(String line : profiler.mfix$getProfilingResults().split("\n")) {
                                    context.getSource().sendSuccess(() -> Component.literal(line), false);
                                }

                                return 1;
                            } else {
                                context.getSource().sendFailure(Component.literal("ModernFix mcfunction profiling is not enabled on this server."));
                                return 0;
                            }
                        }))
        );
    }
}
