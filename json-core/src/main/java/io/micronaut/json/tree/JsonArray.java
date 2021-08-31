package io.micronaut.json.tree;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Public to allow special handling for conversion service.
 */
@Internal
public class JsonArray extends JsonContainer {
    private final List<JsonNode> values;

    JsonArray(List<JsonNode> values) {
        this.values = values;
    }

    @Override
    public JsonToken asToken() {
        return JsonToken.START_ARRAY;
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public JsonNode get(String fieldName) {
        return null;
    }

    @Override
    public JsonNode get(int index) {
        if (index < 0 || index >= size()) {
            return null;
        } else {
            return values.get(index);
        }
    }

    @Override
    public JsonNode path(String fieldName) {
        return JsonMissing.INSTANCE;
    }

    @Override
    public JsonNode path(int index) {
        if (index < 0 || index >= size()) {
            return JsonMissing.INSTANCE;
        } else {
            return values.get(index);
        }
    }

    @Override
    void emit(JsonGenerator generator) throws IOException {
        generator.writeStartArray();
        for (JsonNode value : values) {
            value.emit(generator);
        }
        generator.writeEndArray();
    }

    @Override
    public Iterator<String> fieldNames() {
        return Collections.emptyIterator();
    }

    @Override
    @NonNull
    public Iterator<JsonNode> valueIterator() {
        return values.iterator();
    }

    @Override
    @NonNull
    public Iterator<Map.Entry<String, JsonNode>> entryIterator() {
        throw new IllegalStateException("Not an object");
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JsonArray && ((JsonArray) o).values.equals(values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}
