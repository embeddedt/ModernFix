package org.embeddedt.modernfix.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.embeddedt.modernfix.structure.CachingStructureManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.minecraft.commands.Commands.*;

public class ModernFixCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("modernfix")
                        .then(literal("upgradeStructures")
                        .requires(source -> source.hasPermission(3))
                        .executes(context -> {
                            ServerLevel level = context.getSource().getLevel();
                            if(level == null) {
                                context.getSource().sendFailure(Component.literal("Couldn't find server level"));
                                return 0;
                            }

                            ResourceManager manager = level.getServer().resources.resourceManager();
                            Map<ResourceLocation, Resource> structures = manager.listResources("structures", p -> p.getPath().endsWith(".nbt"));
                            int upgradedNum = 0;
                            Pattern pathPattern = Pattern.compile("^structures/(.*)\\.nbt$");
                            for(Map.Entry<ResourceLocation, Resource> entry : structures.entrySet()) {
                                upgradedNum++;
                                ResourceLocation found = entry.getKey();
                                Matcher matcher = pathPattern.matcher(found.getPath());
                                if(!matcher.matches())
                                    continue;
                                ResourceLocation structureLocation = new ResourceLocation(found.getNamespace(), matcher.group(1));
                                try(InputStream resource = entry.getValue().open()) {
                                    CachingStructureManager.readStructureTag(structureLocation, level.getServer().getFixerUpper(), resource);
                                    context.getSource().sendSuccess(Component.literal("checked " + structureLocation + " (" + upgradedNum + "/" + structures.size() + ")"), false);
                                } catch(IOException e) {
                                    context.getSource().sendFailure(Component.literal("error reading " + structureLocation + " (" + upgradedNum + "/" + structures.size() + ")"));
                                }
                            }

                            context.getSource().sendSuccess(Component.literal("All structures upgraded"), false);

                            return 1;
                        }))
        );
    }
}
