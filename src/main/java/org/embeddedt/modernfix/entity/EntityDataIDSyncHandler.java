package org.embeddedt.modernfix.entity;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.packet.EntityIDSyncPacket;
import org.embeddedt.modernfix.packet.PacketHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityDataIDSyncHandler {
    private static Map<Class<? extends Entity>, List<Pair<String, Integer>>> fieldsToSyncMap;
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @SuppressWarnings("unchecked")
    public static void onDatapackSyncEvent(OnDatapackSyncEvent event) {
        if(event.getPlayer() != null) {
            /* Compute the current set of serializer IDs in use and send them */
            try {
                if(fieldsToSyncMap == null) {
                    fieldsToSyncMap = new HashMap<>();
                    Field entityPoolField = ObfuscationReflectionHelper.findField(SynchedEntityData.class, "field_187232_a");
                    Map<Class<? extends Entity>, Integer> entityPoolMap = (Map<Class<? extends Entity>, Integer>)entityPoolField.get(null);
                    List<Field> fieldsToSync = new ArrayList<>();
                    for(Class<? extends Entity> eClass : entityPoolMap.keySet()) {
                        fieldsToSync.clear();
                        Field[] classFields = eClass.getDeclaredFields();
                        for(Field field : classFields) {
                            if(!Modifier.isStatic(field.getModifiers()))
                                continue;
                            field.setAccessible(true);
                            Object o = field.get(null);
                            if(o != null && EntityDataAccessor.class.isAssignableFrom(o.getClass())) {
                                fieldsToSync.add(field);
                            }
                        }
                        for(Field field : fieldsToSync) {
                            int id = ((EntityDataAccessor<?>)field.get(null)).id;
                            fieldsToSyncMap.computeIfAbsent(eClass, k -> new ArrayList<>()).add(Pair.of(field.getName(), id));
                        }
                    }
                }
                EntityIDSyncPacket packet = new EntityIDSyncPacket(fieldsToSyncMap);
                ModernFix.LOGGER.debug("Sending ID correction packet to client with " + fieldsToSyncMap.size() + " classes");
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(event::getPlayer), packet);
            } catch(ObfuscationReflectionHelper.UnableToFindFieldException | ReflectiveOperationException e) {
                e.printStackTrace();
            }
        }
    }
}
