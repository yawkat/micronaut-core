package io.micronaut.json;

import com.fasterxml.jackson.core.JsonParser;

import java.io.IOException;

public interface Deserializer<T> {
    /**
     * Deserialize from the given {@code decoder}.
     * <p>
     * The decoder {@link JsonParser#currentToken()} should be positioned at the first token of this value.
     *
     * @param decoder The decoder to parse from
     * @return The decoded value
     */
    T deserialize(JsonParser decoder) throws IOException;
}
