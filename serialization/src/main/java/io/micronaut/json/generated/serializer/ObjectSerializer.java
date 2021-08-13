package io.micronaut.json.generated.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.GenericTypeToken;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

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
public final class ObjectSerializer implements Serializer<Object> {
    private final Provider<Serializer<Map<String, Object>>> map;
    private final Provider<Serializer<List<Object>>> list;
    private final Serializer<String> string;
    private final Serializer<Integer> integer;
    private final Serializer<Long> long_;
    private final Serializer<Float> float_;
    private final Serializer<Double> double_;
    private final Serializer<BigInteger> bigInteger;
    private final Serializer<BigDecimal> bigDecimal;
    private final Serializer<Boolean> bool;

    @Inject
    ObjectSerializer(SerializerLocator locator) {
        this.map = locator.findInvariantSerializerProvider(new GenericTypeToken<Map<String, Object>>() {});
        this.list = locator.findInvariantSerializerProvider(new GenericTypeToken<List<Object>>() {});
        this.string = locator.findInvariantSerializer(String.class);
        this.integer = locator.findInvariantSerializer(Integer.class);
        this.long_ = locator.findInvariantSerializer(Long.class);
        this.float_ = locator.findInvariantSerializer(Float.class);
        this.double_ = locator.findInvariantSerializer(Double.class);
        this.bigInteger = locator.findInvariantSerializer(BigInteger.class);
        this.bigDecimal = locator.findInvariantSerializer(BigDecimal.class);
        this.bool = locator.findInvariantSerializer(Boolean.class);
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
