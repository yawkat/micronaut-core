package io.micronaut.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.generated.JsonParseException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Internal
public class JacksonDecoder implements Decoder {
    @Internal
    public final JsonParser parser; // todo: hide

    @Nullable
    private final JacksonDecoder parent;

    private JacksonDecoder child = null;

    private JacksonDecoder(JsonParser parser, @Nullable JacksonDecoder parent) {
        this.parser = parser;
        this.parent = parent;
    }

    public static Decoder create(JsonParser parser) {
        return new JacksonDecoder(parser, null);
    }

    private void checkChild() {
        if (child != null) {
            throw new IllegalStateException("There is still an unfinished child parser");
        }
        if (parent != null && parent.child != this) {
            throw new IllegalStateException("This child parser has already completed");
        }
    }

    private void preDecodeValue() {
        checkChild();
        if (parser.currentToken() == JsonToken.FIELD_NAME) {
            throw new IllegalStateException("Haven't parsed field name yet");
        }
    }

    @Override
    public final void finishStructure() throws IOException {
        checkChild();
        if (parent != null) {
            parent.child = null;
        }
        JsonToken currentToken = parser.currentToken();
        if (currentToken != JsonToken.END_ARRAY && currentToken != JsonToken.END_OBJECT) {
            throw new IllegalStateException("Not all elements have been consumed yet");
        }
        parser.nextToken();
    }

    @Override
    public final boolean hasNextArrayValue() throws IOException {
        checkChild();
        return parser.currentToken() != JsonToken.END_ARRAY;
    }

    @Nullable
    @Override
    public final String decodeKey() throws IOException {
        checkChild();
        JsonToken currentToken = parser.currentToken();
        if (currentToken == JsonToken.END_OBJECT) {
            // stay on the end token, will be handled in finishStructure
            return null;
        }
        if (currentToken != JsonToken.FIELD_NAME) {
            throw new IllegalStateException("Not at a field name");
        }
        String fieldName = parser.getCurrentName();
        parser.nextToken();
        return fieldName;
    }

    @NonNull
    @Override
    public final Decoder decodeArray() throws IOException {
        preDecodeValue();
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw JsonParseException.from(this, "Unexpected token " + parser.currentToken() + ", expected array");
        }
        parser.nextToken();
        return child = new JacksonDecoder(parser, this);
    }

    @NonNull
    @Override
    public final Decoder decodeObject() throws IOException {
        preDecodeValue();
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw JsonParseException.from(this, "Unexpected token " + parser.currentToken() + ", expected object");
        }
        parser.nextToken();
        return child = new JacksonDecoder(parser, this);
    }

    @NonNull
    @Override
    public final String decodeString() throws IOException {
        preDecodeValue();
        switch (parser.currentToken()) {
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
            case VALUE_STRING:
            case VALUE_TRUE:
            case VALUE_FALSE:
                String value = parser.getValueAsString();
                parser.nextToken();
                return value;
            default:
                throw JsonParseException.from(this, "Unexpected token " + parser.currentToken() + ", expected string");
        }
    }

    @Override
    public final boolean decodeBoolean() throws IOException {
        preDecodeValue();
        switch (parser.currentToken()) {
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
            case VALUE_STRING:
            case VALUE_TRUE:
            case VALUE_FALSE:
                boolean value = parser.getValueAsBoolean();
                parser.nextToken();
                return value;
            default:
                throw JsonParseException.from(this, "Unexpected token " + parser.currentToken() + ", expected boolean");
        }
    }

    @Override
    public final byte decodeByte() throws IOException {
        return (byte) decodeInteger(Byte.MIN_VALUE, Byte.MAX_VALUE);
    }

    @Override
    public final short decodeShort() throws IOException {
        return (short) decodeInteger(Short.MIN_VALUE, Short.MAX_VALUE);
    }

    @Override
    public final char decodeChar() throws IOException {
        // todo: string -> char handling
        return (char) decodeInteger(Character.MIN_VALUE, Character.MAX_VALUE);
    }

    @Override
    public final int decodeInt() throws IOException {
        return (int) decodeInteger(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public final long decodeLong() throws IOException {
        return decodeInteger(Long.MIN_VALUE, Long.MAX_VALUE);
    }

    private long decodeInteger(long min, long max) throws IOException {
        preDecodeValue();
        switch (parser.currentToken()) {
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
            case VALUE_STRING:
            case VALUE_TRUE:
            case VALUE_FALSE:
                // todo: better coercion rules
                long value = parser.getValueAsLong();
                parser.nextToken();
                return value;
            default:
                throw JsonParseException.from(this, "Unexpected token " + parser.currentToken() + ", expected integer");
        }
    }

    @Override
    public final float decodeFloat() throws IOException {
        return (float) decodeDouble();
    }

    @Override
    public double decodeDouble() throws IOException {
        preDecodeValue();
        switch (parser.currentToken()) {
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
            case VALUE_STRING:
            case VALUE_TRUE:
            case VALUE_FALSE:
                // todo: better coercion rules
                double value = parser.getValueAsDouble();
                parser.nextToken();
                return value;
            default:
                throw JsonParseException.from(this, "Unexpected token " + parser.currentToken() + ", expected float");
        }
    }

    @NonNull
    @Override
    public final BigInteger decodeBigInteger() throws IOException {
        preDecodeValue();
        BigInteger value;
        switch (parser.currentToken()) {
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                value = parser.getBigIntegerValue();
                break;
            case VALUE_STRING:
                try {
                    value = new BigInteger(parser.getText());
                } catch (NumberFormatException e) {
                    // match behavior of getValueAsInt
                    value = BigInteger.ZERO;
                }
                break;
            case VALUE_TRUE:
                value = BigInteger.ONE;
                break;
            case VALUE_FALSE:
                value = BigInteger.ZERO;
                break;
            default:
                throw JsonParseException.from(this, "Unexpected token " + parser.currentToken() + ", expected integer");
        }
        parser.nextToken();
        return value;
    }

    @NonNull
    @Override
    public final BigDecimal decodeBigDecimal() throws IOException {
        preDecodeValue();
        BigDecimal value;
        switch (parser.currentToken()) {
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                value = parser.getDecimalValue();
                break;
            case VALUE_STRING:
                try {
                    value = new BigDecimal(parser.getText());
                } catch (NumberFormatException e) {
                    // match behavior of getValueAsDouble
                    value = BigDecimal.ZERO;
                }
                break;
            case VALUE_TRUE:
                value = BigDecimal.ONE;
                break;
            case VALUE_FALSE:
                value = BigDecimal.ZERO;
                break;
            default:
                throw JsonParseException.from(this, "Unexpected token " + parser.currentToken() + ", expected float");
        }
        parser.nextToken();
        return value;
    }

    @Override
    public final boolean decodeNull() throws IOException {
        preDecodeValue();
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            parser.nextToken();
            return true;
        } else {
            return false;
        }
    }

    @Nullable
    @Override
    public final Object decodeArbitrary() throws IOException {
        preDecodeValue();
        switch (parser.currentToken()) {
            case START_OBJECT:
                return decodeArbitraryMap(decodeObject());
            case START_ARRAY:
                return decodeArbitraryList(decodeArray());
            case VALUE_STRING:
                return decodeString();
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                switch (parser.getNumberType()) {
                    case INT:
                        return decodeInt();
                    case LONG:
                        return decodeLong();
                    case BIG_INTEGER:
                        return decodeBigInteger();
                    case FLOAT:
                        return decodeFloat();
                    case DOUBLE:
                        return decodeDouble();
                    case BIG_DECIMAL:
                        return decodeBigDecimal();
                    default:
                        throw new AssertionError(parser.getNumberType());
                }
            case VALUE_TRUE:
            case VALUE_FALSE:
                return decodeBoolean();
            case VALUE_NULL:
                return null;
            default:
                throw JsonParseException.from(this, "Unexpected token " + parser.currentToken() + ", expected value");
        }
    }

    private static Map<String, Object> decodeArbitraryMap(Decoder elementDecoder) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        while (true) {
            String key = elementDecoder.decodeKey();
            if (key == null) {
                break;
            }
            result.put(key, elementDecoder.decodeArbitrary());
        }
        elementDecoder.finishStructure();
        return result;
    }

    private static List<Object> decodeArbitraryList(Decoder elementDecoder) throws IOException {
        List<Object> result = new ArrayList<>();
        while (elementDecoder.hasNextArrayValue()) {
            result.add(elementDecoder.decodeArbitrary());
        }
        elementDecoder.finishStructure();
        return result;
    }

    @Override
    public final void skipValue() throws IOException {
        preDecodeValue();
        parser.skipChildren();
        parser.nextToken();
    }
}
