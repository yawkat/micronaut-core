package io.micronaut.jackson;

import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.json.GenericDeserializationConfig;

public final class DatabindUtil {
    private DatabindUtil() {}

    public static GenericDeserializationConfig toGenericConfig(ObjectMapper objectMapper) {
        return toGenericConfig(objectMapper.getDeserializationConfig())
                .withFactory(objectMapper.getFactory());
    }

    public static GenericDeserializationConfig toGenericConfig(DeserializationConfig config) {
        return GenericDeserializationConfig.DEFAULT
                .withUseBigDecimalForFloats(config.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS))
                .withUseBigIntegerForInts(config.isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS));
    }
}
