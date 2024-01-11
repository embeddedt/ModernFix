package org.embeddedt.modernfix.packet;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.embeddedt.modernfix.ModernFix;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class EntityIDSyncPacket implements CustomPacketPayload {
    public static final ResourceLocation ID = new ResourceLocation(ModernFix.MODID, "entity_id_sync");
    private Map<Class<? extends Entity>, List<Pair<String, Integer>>> map;

    public EntityIDSyncPacket(Map<Class<? extends Entity>, List<Pair<String, Integer>>> map) {
        this.map = map;
    }

    public Map<Class<? extends Entity>, List<Pair<String, Integer>>> getFieldInfo() {
        return this.map;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(map.keySet().size());
        for(Map.Entry<Class<? extends Entity>, List<Pair<String, Integer>>> entry : map.entrySet()) {
            buf.writeUtf(entry.getKey().getName());
            buf.writeVarInt(entry.getValue().size());
            for(Pair<String, Integer> field : entry.getValue()) {
                buf.writeUtf(field.getFirst());
                buf.writeVarInt(field.getSecond());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public EntityIDSyncPacket(FriendlyByteBuf buf) {
        EntityIDSyncPacket self = this;
        int numEntityClasses = buf.readVarInt();
        for(int i = 0; i < numEntityClasses; i++) {
            String clzName = buf.readUtf();
            try {
                Class<?> clz;
                try {
                    clz = Class.forName(clzName);
                } catch(ClassNotFoundException e) {
                    ModernFix.LOGGER.warn("Entity class not found: {}", clzName);
                    break;
                }
                if(!Entity.class.isAssignableFrom(clz)) {
                    ModernFix.LOGGER.error("Not an entity: " + clzName);
                    break;
                }
                int numFields = buf.readVarInt();
                for(int j = 0; j < numFields; j++) {
                    String fieldName = buf.readUtf();
                    int id = buf.readVarInt();
                    Field f = clz.getDeclaredField(fieldName);
                    if(!Modifier.isStatic(f.getModifiers()))
                        continue;
                    f.setAccessible(true);
                    if(!EntityDataAccessor.class.isAssignableFrom(f.get(null).getClass())) {
                        ModernFix.LOGGER.error("Not a data accessor field: " + clz + "." + fieldName);
                        continue;
                    }
                    self.map.computeIfAbsent((Class<? extends Entity>)clz, k -> new ArrayList<>()).add(Pair.of(fieldName, id));
                }
            } catch(ReflectiveOperationException e) {
                ModernFix.LOGGER.error("Error deserializing packet", e);
            }
        }
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
