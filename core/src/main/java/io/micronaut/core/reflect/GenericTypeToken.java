package io.micronaut.core.reflect;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class GenericTypeToken<T> {
    private final Type type;

    protected GenericTypeToken() {
        Type parameterization = GenericTypeUtils.findParameterization(getClass(), GenericTypeToken.class);
        assert parameterization != null;
        this.type = ((ParameterizedType) parameterization).getActualTypeArguments()[0];
    }

    public final Type getType() {
        return type;
    }
}
