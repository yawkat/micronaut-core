package io.micronaut.json.generated.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.ObjectCodec;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.json.Decoder;
import io.micronaut.json.Deserializer;
import io.micronaut.json.Serializer;
import io.micronaut.json.generated.JsonParseException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Fallback {@link Serializer} for general {@link Object} values. For deserialization, deserializes to standard types
 * like {@link Number}, {@link String}, {@link Boolean}, {@link Map} and {@link List}. For serialization, serializes
 * using the configured object codec ({@link JsonGenerator#getCodec()}).
 * <p>
 * This class is used in multiple scenarios:
 * <ul>
 *     <li>When the user has an {@link Object} property in a serializable bean.</li>
 *     <li>When the user explicitly calls {@link ObjectCodec#writeValue}{@code (gen, }{@link Object}{@code .class)}</li>
 * </ul>
 */
@Internal
@Secondary
public final class ObjectSerializer implements Serializer<Object>, Deserializer<Object> {
    @Override
    public Object deserialize(Decoder decoder) throws IOException {
        if (decoder.decodeNull()) {
            throw JsonParseException.from(decoder, "Unexpected null value");
        }
        return decoder.decodeArbitrary();
    }

    @Override
    public void serialize(JsonGenerator encoder, Object value) throws IOException {
        encoder.writeObject(value);
    }
}
