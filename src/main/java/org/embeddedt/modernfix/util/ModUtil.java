package org.embeddedt.modernfix.util;

import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.api.EventListenerHelper;
import net.minecraftforge.eventbus.api.IEventListener;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.embeddedt.modernfix.ModernFix;

import java.util.*;
import java.util.function.Supplier;

public class ModUtil {
    private static final Set<Class<?>> erroredContexts = new HashSet<>();
    private static boolean busListensToEvent(EventBus bus, Class<?> eventClazz) {
        try {
            int busID = ObfuscationReflectionHelper.getPrivateValue(EventBus.class, bus, "busID");
            return EventListenerHelper.getListenerList(eventClazz).getListeners(busID).length > 0;
        } catch(Exception e) {
            ModernFix.LOGGER.error(e);
            return false;
        }
    }
    public static Collection<String> findAllModsListeningToEvent(Class<?> eventClazz) {
        Set<String> modsListening = new HashSet<>();
        ModList.get().forEachModContainer((modId, container) -> {
            Supplier<?> languageExtensionSupplier = ObfuscationReflectionHelper.getPrivateValue(ModContainer.class, container, "contextExtension");
            Object context = languageExtensionSupplier.get();
            if(context == null)
                return;
            if(context instanceof FMLJavaModLoadingContext) {
                if(busListensToEvent((EventBus)((FMLJavaModLoadingContext) context).getModEventBus(), eventClazz)) {
                    modsListening.add(modId);
                }
            } else {
                synchronized(erroredContexts) {
                    if(!erroredContexts.contains(context.getClass())) {
                        ModernFix.LOGGER.warn("Unknown modloading context: " + context.getClass().getName());
                        erroredContexts.add(context.getClass());
                    }
                }
            }
        });
        return modsListening;
    }
}
