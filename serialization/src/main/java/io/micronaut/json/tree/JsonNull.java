package io.micronaut.json.tree;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.micronaut.core.annotation.NonNull;

import java.io.IOException;

class JsonNull extends JsonScalar {
    static final JsonNull INSTANCE = new JsonNull();

    private JsonNull() {}

    @NonNull
    @Override
    public String coerceStringValue() {
        return "null";
    }

    @Override
    public boolean isNull() {
        return true;
    }

    @Override
    public JsonToken asToken() {
        return JsonToken.VALUE_NULL;
    }

    @Override
    public JsonParser.NumberType numberType() {
        return null;
    }

    @Override
    void emit(JsonGenerator generator) throws IOException {
        generator.writeNull();
    }
}
