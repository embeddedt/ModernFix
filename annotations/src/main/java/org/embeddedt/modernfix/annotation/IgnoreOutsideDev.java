package org.embeddedt.modernfix.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The config system will ignore mixins with this annotation when generating config options unless running
 * in a dev environment.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface IgnoreOutsideDev {
}
