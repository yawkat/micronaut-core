package io.micronaut.json.tree;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import io.micronaut.core.annotation.NonNull;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

class JsonObject extends JsonContainer {
    private final Map<String, JsonNode> values;

    JsonObject(Map<String, JsonNode> values) {
        this.values = values;
    }

    @Override
    public JsonToken asToken() {
        return JsonToken.START_OBJECT;
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
    public JsonNode path(String fieldName) {
        return values.getOrDefault(fieldName, JsonMissing.INSTANCE);
    }

    @Override
    public JsonNode path(int index) {
        return JsonMissing.INSTANCE;
    }

    @Override
    public Iterator<String> fieldNames() {
        return values.keySet().iterator();
    }

    @Override
    @NonNull
    public Iterator<JsonNode> valueIterator() {
        return values.values().iterator();
    }

    @Override
    @NonNull
    public Iterator<Map.Entry<String, JsonNode>> entryIterator() {
        return values.entrySet().iterator();
    }

    @Override
    void emit(JsonGenerator generator) throws IOException {
        generator.writeStartObject();
        for (Map.Entry<String, JsonNode> entry : values.entrySet()) {
            generator.writeFieldName(entry.getKey());
            entry.getValue().emit(generator);
        }
        generator.writeEndObject();
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
