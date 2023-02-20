package org.embeddedt.modernfix.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.embeddedt.modernfix.ModernFix;

import java.util.HashMap;
import java.util.Set;

@Mod.EventBusSubscriber(modid = ModernFix.MODID)
public class KubeUtil {
    public static final HashMap<String, Set<ResourceLocation>> matchedIdsForRegex = new HashMap<>();

    @SubscribeEvent
    public static void clearRegexCache(AddReloadListenerEvent event) {
        matchedIdsForRegex.clear();
    }
}
