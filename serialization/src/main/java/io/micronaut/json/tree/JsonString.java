package io.micronaut.json.tree;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.micronaut.core.annotation.NonNull;

import java.io.IOException;

class JsonString extends JsonScalar {
    @NonNull
    private final String value;

    JsonString(@NonNull String value) {
        this.value = value;
    }

    @Override
    public JsonToken asToken() {
        return JsonToken.VALUE_STRING;
    }

    @Override
    public JsonParser.NumberType numberType() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JsonString && ((JsonString) o).value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean isString() {
        return true;
    }

    @Override
    public String getStringValue() {
        return value;
    }

    @Override
    void emit(JsonGenerator generator) throws IOException {
        generator.writeString(value);
    }
}
