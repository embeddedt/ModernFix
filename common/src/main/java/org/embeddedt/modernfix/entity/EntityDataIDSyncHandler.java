package org.embeddedt.modernfix.entity;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.packet.EntityIDSyncPacket;
import org.embeddedt.modernfix.platform.ModernFixPlatformHooks;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityDataIDSyncHandler {
    private static Map<Class<? extends Entity>, List<Pair<String, Integer>>> fieldsToSyncMap;

    @SuppressWarnings("unchecked")
    public static void onDatapackSyncEvent(ServerPlayer targetPlayer) {
        if(targetPlayer != null) {
            /* Compute the current set of serializer IDs in use and send them */
            if(fieldsToSyncMap == null) {
                fieldsToSyncMap = new HashMap<>();
                Map<Class<? extends Entity>, Integer> entityPoolMap = SynchedEntityData.ENTITY_ID_POOL;
                List<Field> fieldsToSync = new ArrayList<>();
                for(Class<? extends Entity> eClass : entityPoolMap.keySet()) {
                    fieldsToSync.clear();
                    try {
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
                    } catch(Throwable e) {
                        ModernFix.LOGGER.error("Skipping entity ID sync for {}: {}", eClass.getName(), e);
                    }
                }
            }
            EntityIDSyncPacket packet = new EntityIDSyncPacket(fieldsToSyncMap);
            ModernFix.LOGGER.debug("Sending ID correction packet to client with " + fieldsToSyncMap.size() + " classes");
            ModernFixPlatformHooks.INSTANCE.sendPacket(targetPlayer, packet);
        }
    }
}
