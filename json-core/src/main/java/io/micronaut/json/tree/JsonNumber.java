package io.micronaut.json.tree;

import io.micronaut.core.annotation.NonNull;

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

    @NonNull
    @Override
    public String coerceStringValue() {
        return value.toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JsonNumber && ((JsonNumber) o).value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
