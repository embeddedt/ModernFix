package org.embeddedt.modernfix.entity;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

public class AttributeInstanceTemplates {
    private static final ObjectOpenCustomHashSet<AttributeInstance> INTERNER = new ObjectOpenCustomHashSet<>(new Hash.Strategy<>() {
        @Override
        public int hashCode(AttributeInstance o) {
            if (o == null) {
                return 0;
            }
            int h = System.identityHashCode(o.getAttribute());
            h = 31 * h + Double.hashCode(o.getBaseValue());
            h = 31 * h + o.getModifiers().hashCode();
            return h;
        }

        @Override
        public boolean equals(AttributeInstance a, AttributeInstance b) {
            if (a == b) {
                return true;
            }
            if (a == null || b == null) {
                return false;
            }
            return a.getAttribute() == b.getAttribute()
                    && a.getBaseValue() == b.getBaseValue()
                    && a.getModifiers().equals(b.getModifiers());
        }
    });

    public static AttributeInstance intern(AttributeInstance a) {
        if (a == null || a.getClass() != AttributeInstance.class) {
            return a;
        }
        synchronized (INTERNER) {
            return INTERNER.addOrGet(a);
        }
    }
}
