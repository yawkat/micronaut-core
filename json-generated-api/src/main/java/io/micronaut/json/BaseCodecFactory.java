package io.micronaut.json;

import java.lang.reflect.Type;
import java.util.function.Function;

interface BaseCodecFactory {
    Type getGenericType();

    Object newInstance(
            SerializerLocator locator,
            Function<String, Type> getTypeParameter
    );
}
