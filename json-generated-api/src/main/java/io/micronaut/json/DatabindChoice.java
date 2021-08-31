package io.micronaut.json;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Internal
public class DatabindChoice {
    private static final String PROPERTY_KEY = "micronaut.databind";
    private static final String PROPERTY_VALUE = "generated";

    private static final boolean ENABLED;

    static {
        boolean jacksonOnClasspath;
        try {
            Class.forName("io.micronaut.jackson.ObjectMapperFactory");
            jacksonOnClasspath = true;
        } catch (ClassNotFoundException ignored) {
            jacksonOnClasspath = false;
        }

        String prop = System.getProperty(PROPERTY_KEY);
        if (prop != null) {
            if (prop.equals(PROPERTY_VALUE)) {
                ENABLED = true;
            } else if (prop.equals("jackson")) {
                ENABLED = false;
                if (!jacksonOnClasspath) {
                    throw new IllegalStateException("Jackson not on classpath, but explicitly requested in system property " + PROPERTY_KEY);
                }
            } else {
                throw new IllegalStateException("Invalid value for system property " + PROPERTY_KEY);
            }
        } else {
            ENABLED = !jacksonOnClasspath;
        }
    }

    public static boolean isGeneratedDatabindEnabled() {
        return ENABLED;
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Requires(property = PROPERTY_KEY, notEquals = PROPERTY_VALUE)
    public @interface RequiresJackson {
    }
}
