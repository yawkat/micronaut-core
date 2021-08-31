package io.micronaut.json.annotation;

import io.micronaut.core.annotation.Internal;

import java.lang.annotation.*;

/**
 * Annotation to trigger serializer generation for another class.
 */
@Internal
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Repeatable(SerializationMixin.Repeated.class)
public @interface SerializationMixin {
    Class<?> forClass();

    SerializableBean config() default @SerializableBean;

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.SOURCE)
    @interface Repeated {
        SerializationMixin[] value();
    }
}
