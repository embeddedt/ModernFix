package org.embeddedt.modernfix.neoforge.classloading;

/**
 * Sometimes mods have classes that circularly reference each other. If each of these classes ends up being loaded
 * from two mods, a deadlock occurs.
 *
 * To avoid this problem we maintain a list of classes that should be loaded early and do it via Class.forName.
 */
public class ClassLoadHack {
    private static final String[] classesToLoadEarly = new String[] {
        "team.creative.creativecore.common.config.ConfigTypeConveration",
        "team.creative.creativecore.common.util.ingredient.CreativeIngredient"
    };

    public static void loadModClasses() {
        for(String clzName : classesToLoadEarly) {
            try {
                Class.forName(clzName);
            } catch(Throwable e) {
                if(!(e instanceof ClassNotFoundException)) {
                    e.printStackTrace();
                }
            }
        }
    }
}
