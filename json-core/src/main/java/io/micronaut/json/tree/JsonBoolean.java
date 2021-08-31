package io.micronaut.json.tree;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.micronaut.core.annotation.NonNull;

import java.io.IOException;

class JsonBoolean extends JsonScalar {
    private static final JsonBoolean TRUE = new JsonBoolean(true);
    private static final JsonBoolean FALSE = new JsonBoolean(false);

    private final boolean value;

    private JsonBoolean(boolean value) {
        this.value = value;
    }

    static JsonBoolean valueOf(boolean value) {
        return value ? TRUE : FALSE;
    }

    @Override
    public JsonToken asToken() {
        return value ? JsonToken.VALUE_TRUE : JsonToken.VALUE_FALSE;
    }

    @Override
    public JsonParser.NumberType numberType() {
        return null;
    }

    @NonNull
    @Override
    public String coerceStringValue() {
        return Boolean.toString(value);
    }

    @Override
    public boolean isBoolean() {
        return true;
    }

    @Override
    public boolean getBooleanValue() {
        return value;
    }

    @Override
    void emit(JsonGenerator generator) throws IOException {
        generator.writeBoolean(value);
    }
}
