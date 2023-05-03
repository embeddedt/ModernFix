package org.embeddedt.modernfix.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.embeddedt.modernfix.structure.CachingStructureManager;

import java.io.IOException;
import java.util.Collection;
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
                                context.getSource().sendFailure(new TextComponent("Couldn't find server level"));
                                return 0;
                            }

                            ResourceManager manager = level.getServer().resources.resourceManager();
                            Collection<ResourceLocation> structures = manager.listResources("structures", p -> p.endsWith(".nbt"));
                            int upgradedNum = 0;
                            Pattern pathPattern = Pattern.compile("^structures/(.*)\\.nbt$");
                            for(ResourceLocation found : structures) {
                                upgradedNum++;
                                Matcher matcher = pathPattern.matcher(found.getPath());
                                if(!matcher.matches())
                                    continue;
                                ResourceLocation structureLocation = new ResourceLocation(found.getNamespace(), matcher.group(1));
                                try(Resource resource = manager.getResource(found)) {
                                    CachingStructureManager.readStructureTag(structureLocation, level.getServer().getFixerUpper(), resource.getInputStream());
                                    context.getSource().sendSuccess(new TextComponent("checked " + structureLocation + " (" + upgradedNum + "/" + structures.size() + ")"), false);
                                } catch(IOException e) {
                                    context.getSource().sendFailure(new TextComponent("error reading " + structureLocation + " (" + upgradedNum + "/" + structures.size() + ")"));
                                }
                            }

                            context.getSource().sendSuccess(new TextComponent("All structures upgraded"), false);

                            return 1;
                        }))
        );
    }
}
