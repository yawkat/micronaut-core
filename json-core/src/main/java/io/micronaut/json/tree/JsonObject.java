package io.micronaut.json.tree;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.util.Map;

/**
 * Public to allow special handling for conversion service.
 */
@Internal
public class JsonObject extends JsonContainer {
    private final Map<String, JsonNode> values;

    JsonObject(Map<String, JsonNode> values) {
        this.values = values;
    }

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public JsonNode get(String fieldName) {
        return values.get(fieldName);
    }

    @Override
    public JsonNode get(int index) {
        return null;
    }

    @Override
    @NonNull
    public Iterable<JsonNode> values() {
        return values.values();
    }

    @Override
    @NonNull
    public Iterable<Map.Entry<String, JsonNode>> entries() {
        return values.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JsonObject && ((JsonObject) o).values.equals(values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}
