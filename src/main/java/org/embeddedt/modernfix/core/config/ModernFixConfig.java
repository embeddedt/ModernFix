package org.embeddedt.modernfix.core.config;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.embeddedt.modernfix.ModernFix;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = ModernFix.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModernFixConfig {
    private static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
    public static ForgeConfigSpec COMMON_CONFIG;

    public static ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLIST_ASYNC_JEI_PLUGINS;

    public static Set<ResourceLocation> jeiPluginBlacklist;

    static {
        List<? extends String> empty = Collections.emptyList();
        Predicate<Object> locationValidator = o -> o instanceof String && ((String)o).contains(":");
        BLACKLIST_ASYNC_JEI_PLUGINS = COMMON_BUILDER
                .comment("These JEI plugins will be loaded on the main thread")
                .defineList("blacklist_async_jei_plugins", ImmutableList.of(
                        "jepb:jei_plugin"
                ), locationValidator);
    }

    static {
        COMMON_CONFIG = COMMON_BUILDER.build();
    }

    @SubscribeEvent
    public static void onModConfigEvent(final ModConfig.ModConfigEvent configEvent) {
        if (configEvent.getConfig().getSpec() == COMMON_CONFIG) {
            bakeConfig();
        }
    }

    public static void bakeConfig() {
        jeiPluginBlacklist = BLACKLIST_ASYNC_JEI_PLUGINS.get().stream().map(ResourceLocation::new).collect(Collectors.toSet());
    }

}
