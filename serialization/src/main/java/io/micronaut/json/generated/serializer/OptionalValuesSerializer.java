package io.micronaut.json.generated.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import io.micronaut.core.reflect.GenericTypeFactory;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Function;

class OptionalValuesSerializer<V> implements Serializer<OptionalValues<V>> {
    private final Serializer<? super V> valueSerializer;

    public OptionalValuesSerializer(Serializer<? super V> valueSerializer) {
        this.valueSerializer = valueSerializer;
    }

    @Override
    public void serialize(JsonGenerator encoder, OptionalValues<V> value) throws IOException {
        encoder.writeStartObject();
        for (CharSequence key : value) {
            Optional<V> opt = value.get(key);
            if (opt.isPresent()) {
                encoder.writeFieldName(key.toString());
                valueSerializer.serialize(encoder, opt.get());
            }
        }
        encoder.writeEndObject();
    }

    @Singleton
    static class Factory implements Serializer.Factory {
        @Override
        public Type getGenericType() {
            return GenericTypeFactory.makeParameterizedTypeWithOwner(null, OptionalValues.class, OptionalValuesSerializer.class.getTypeParameters()[0]);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public Serializer<? super OptionalValues<?>> newInstance(SerializerLocator locator, Function<String, Type> getTypeParameter) {
            return new OptionalValuesSerializer(locator.findContravariantSerializer(getTypeParameter.apply("V")));
        }
    }
}
