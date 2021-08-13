package io.micronaut.json.generated.serializer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker class to tell the generator that we should generate the primitive serializers here.
 */
@PrimitiveGenerators.GeneratePrimitiveSerializers
class PrimitiveGenerators {
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.SOURCE)
    @interface GeneratePrimitiveSerializers {
    }
}
