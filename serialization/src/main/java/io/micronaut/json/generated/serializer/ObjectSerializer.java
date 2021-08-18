package io.micronaut.json.generated.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.GenericTypeToken;
import io.micronaut.json.Deserializer;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
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
    private final Provider<Deserializer<Map<String, Object>>> map;
    private final Provider<Deserializer<List<Object>>> list;
    private final Deserializer<String> string;
    private final Deserializer<Integer> integer;
    private final Deserializer<Long> long_;
    private final Deserializer<Float> float_;
    private final Deserializer<Double> double_;
    private final Deserializer<BigInteger> bigInteger;
    private final Deserializer<BigDecimal> bigDecimal;
    private final Deserializer<Boolean> bool;

    @Inject
    ObjectSerializer(SerializerLocator locator) {
        this.map = locator.findInvariantDeserializerProvider(new GenericTypeToken<Map<String, Object>>() {});
        this.list = locator.findInvariantDeserializerProvider(new GenericTypeToken<List<Object>>() {});
        this.string = locator.findInvariantDeserializer(String.class);
        this.integer = locator.findInvariantDeserializer(Integer.class);
        this.long_ = locator.findInvariantDeserializer(Long.class);
        this.float_ = locator.findInvariantDeserializer(Float.class);
        this.double_ = locator.findInvariantDeserializer(Double.class);
        this.bigInteger = locator.findInvariantDeserializer(BigInteger.class);
        this.bigDecimal = locator.findInvariantDeserializer(BigDecimal.class);
        this.bool = locator.findInvariantDeserializer(Boolean.class);
    }

    @Override
    public Object deserialize(JsonParser decoder) throws IOException {
        switch (decoder.currentToken()) {
            case START_OBJECT:
                return map.get().deserialize(decoder);
            case START_ARRAY:
                return list.get().deserialize(decoder);
            case VALUE_STRING:
                return string.deserialize(decoder);
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                switch (decoder.getNumberType()) {
                    case INT:
                        return integer.deserialize(decoder);
                    case LONG:
                        return long_.deserialize(decoder);
                    case BIG_INTEGER:
                        return bigInteger.deserialize(decoder);
                    case FLOAT:
                        return float_.deserialize(decoder);
                    case DOUBLE:
                        return double_.deserialize(decoder);
                    case BIG_DECIMAL:
                        return bigDecimal.deserialize(decoder);
                }
                break;
            case VALUE_TRUE:
            case VALUE_FALSE:
                return bool.deserialize(decoder);
            case VALUE_NULL:
                // we don't handle nulls here
        }
        throw new JsonParseException(decoder, "Unexpected token: Expected value, got " + decoder.currentToken());
    }

    @Override
    public void serialize(JsonGenerator encoder, Object value) throws IOException {
        encoder.writeObject(value);
    }
}
