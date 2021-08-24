package io.micronaut.json;

import com.fasterxml.jackson.core.JsonParser;
import io.micronaut.core.reflect.GenericTypeUtils;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.Function;

public interface Deserializer<T> {
    /**
     * Deserialize from the given {@code decoder}.
     * <p>
     * The decoder {@link JsonParser#currentToken()} should be positioned at the first token of this value.
     *
     * @param decoder The decoder to parse from
     * @return The decoded value
     */
    T deserialize(JsonParser decoder) throws IOException;

    interface Factory extends BaseCodecFactory {
        @Override
        default Type getGenericType() {
            Type parameterization = GenericTypeUtils.findParameterization(getClass(), Factory.class);
            assert parameterization != null;
            return ((ParameterizedType) parameterization).getActualTypeArguments()[0];
        }

        @Override
        Deserializer<?> newInstance(SerializerLocator locator, Function<String, Type> getTypeParameter);
    }
}
