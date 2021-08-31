package io.micronaut.json.tree;

import io.micronaut.core.annotation.NonNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

abstract class JsonScalar extends JsonNode {
    @Override
    public int size() {
        return 0;
    }

    @Override
    @NonNull
    public Iterator<JsonNode> valueIterator() {
        throw new IllegalStateException("Not a container");
    }

    @Override
    @NonNull
    public Iterator<Map.Entry<String, JsonNode>> entryIterator() {
        throw new IllegalStateException("Not an object");
    }

    @Override
    public boolean isValueNode() {
        return true;
    }

    @Override
    public JsonNode get(String fieldName) {
        return null;
    }

    @Override
    public JsonNode get(int index) {
        return null;
    }

    @Override
    public JsonNode path(String fieldName) {
        return JsonMissing.INSTANCE;
    }

    @Override
    public JsonNode path(int index) {
        return JsonMissing.INSTANCE;
    }

    @Override
    public Iterator<String> fieldNames() {
        return Collections.emptyIterator();
    }
}
