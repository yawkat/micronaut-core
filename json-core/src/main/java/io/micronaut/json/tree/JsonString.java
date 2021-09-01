package io.micronaut.json.tree;

import io.micronaut.core.annotation.NonNull;

class JsonString extends JsonScalar {
    @NonNull
    private final String value;

    JsonString(@NonNull String value) {
        this.value = value;
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

    @NonNull
    @Override
    public String getStringValue() {
        return value;
    }

    @NonNull
    @Override
    public String coerceStringValue() {
        return value;
    }
}
