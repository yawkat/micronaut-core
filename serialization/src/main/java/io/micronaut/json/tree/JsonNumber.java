package io.micronaut.json.tree;

import com.fasterxml.jackson.core.*;
import io.micronaut.core.annotation.NonNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

class JsonNumber extends JsonScalar {
    @NonNull
    private final Number value;

    JsonNumber(@NonNull Number value) {
        this.value = value;
    }

    @Override
    public boolean isNumber() {
        return true;
    }

    @Override
    @NonNull
    public Number getNumberValue() {
        return value;
    }

    @Override
    public JsonToken asToken() {
        JsonParser.NumberType numberType = numberType();
        if (numberType == JsonParser.NumberType.BIG_DECIMAL || numberType == JsonParser.NumberType.FLOAT || numberType == JsonParser.NumberType.DOUBLE) {
            return JsonToken.VALUE_NUMBER_FLOAT;
        } else {
            return JsonToken.VALUE_NUMBER_INT;
        }
    }

    @Override
    public JsonParser.NumberType numberType() {
        if (value instanceof BigDecimal) {
            return JsonParser.NumberType.BIG_DECIMAL;
        } else if (value instanceof Double) {
            return JsonParser.NumberType.DOUBLE;
        } else if (value instanceof Float) {
            return JsonParser.NumberType.FLOAT;
        } else if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
            return JsonParser.NumberType.INT;
        } else if (value instanceof Long) {
            return JsonParser.NumberType.LONG;
        } else if (value instanceof BigInteger) {
            return JsonParser.NumberType.BIG_INTEGER;
        } else {
            throw new IllegalStateException("Unknown number type " + value.getClass().getName());
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JsonNumber && ((JsonNumber) o).value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    void emit(JsonGenerator generator) throws IOException {
        if (value instanceof BigDecimal) {
            generator.writeNumber((BigDecimal) value);
        } else if (value instanceof Double) {
            generator.writeNumber(value.doubleValue());
        } else if (value instanceof Float) {
            generator.writeNumber(value.floatValue());
        } else if (value instanceof Integer) {
            generator.writeNumber(value.intValue());
        } else if (value instanceof Byte || value instanceof Short) {
            generator.writeNumber(value.shortValue());
        } else if (value instanceof Long) {
            generator.writeNumber(value.longValue());
        } else if (value instanceof BigInteger) {
            generator.writeNumber((BigInteger) value);
        } else {
            throw new IllegalStateException("Unknown number type " + value.getClass().getName());
        }
    }
}
