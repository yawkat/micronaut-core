package io.micronaut.core.reflect;

import java.lang.reflect.Type;
import java.util.Map;

class InnerOuter<T> {
    abstract class Inner<U> implements Map<T, U> {}

    static final Type INCOMPATIBLE_TYPE = new GenericTypeToken<InnerOuter<String>.Inner<Number>>() {}.getType();
    static final Type COMPATIBLE_TYPE = new GenericTypeToken<InnerOuter<String>.Inner<Integer>>() {}.getType();

    abstract class InnerExt extends InnerOuter<String>.Inner<Integer> {}
}
