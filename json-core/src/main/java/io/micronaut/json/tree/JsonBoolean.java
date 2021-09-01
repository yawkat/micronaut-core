package io.micronaut.json.tree;

import io.micronaut.core.annotation.NonNull;

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
}
