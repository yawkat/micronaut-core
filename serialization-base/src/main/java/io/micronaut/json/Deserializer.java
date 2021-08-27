package io.micronaut.json;

import com.fasterxml.jackson.core.JsonParser;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.function.Function;

public interface Deserializer<T> {
    /**
     * Deserialize from the given {@code decoder}.
     * <p>
     * The decoder {@link JsonParser#currentToken()} should be positioned at the first token of this value.
     *
     * @param decoder The decoder to parse from
     * @return The decoded value
     */
    @Deprecated
    default T deserialize(JsonParser decoder) throws IOException {
        return deserialize(JacksonDecoder.create(decoder));
    }

    T deserialize(Decoder decoder) throws IOException;

    default boolean supportsNullDeserialization() {
        return false;
    }

    interface Factory extends BaseCodecFactory {
        @Override
        Type getGenericType();

        @Override
        Deserializer<?> newInstance(SerializerLocator locator, Function<String, Type> getTypeParameter);
    }
}
