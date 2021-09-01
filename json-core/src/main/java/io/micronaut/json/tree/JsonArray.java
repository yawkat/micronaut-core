package io.micronaut.json.tree;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.io.IOException;
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
    @NonNull
    public Iterable<JsonNode> values() {
        return values;
    }

    @Override
    @NonNull
    public Iterable<Map.Entry<String, JsonNode>> entries() {
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
