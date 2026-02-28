package org.embeddedt.modernfix.forge.capability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applied to generated provider fields whose declared type has been widened to
 * {@link net.minecraftforge.common.capabilities.ICapabilityProvider} because the
 * concrete class is non-public or hidden. The value records the original type name.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface OriginalType {
    String value();
}
