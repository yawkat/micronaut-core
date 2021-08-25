package io.micronaut.json.generated.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import io.micronaut.core.reflect.GenericTypeFactory;
import io.micronaut.core.value.OptionalMultiValues;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.json.JsonConfiguration;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

class OptionalMultiValuesSerializer<V> implements Serializer<OptionalMultiValues<V>> {
    private final boolean alwaysSerializeErrorsAsList;
    private final Serializer<? super V> valueSerializer;
    private final Serializer<? super List<V>> listSerializer;

    public OptionalMultiValuesSerializer(JsonConfiguration jacksonConfiguration, Serializer<? super V> valueSerializer, Serializer<? super List<V>> listSerializer) {
        this.alwaysSerializeErrorsAsList = jacksonConfiguration.isAlwaysSerializeErrorsAsList();
        this.valueSerializer = valueSerializer;
        this.listSerializer = listSerializer;
    }

    @Override
    public void serialize(JsonGenerator encoder, OptionalMultiValues<V> value) throws IOException {
        encoder.writeStartObject();
        for (CharSequence key : value) {
            Optional<? extends List<V>> opt = value.get(key);
            if (opt.isPresent()) {
                String fieldName = key.toString();
                encoder.writeFieldName(fieldName);
                List<V> list = opt.get();
                if (list.size() == 1 && (list.get(0).getClass() != JsonError.class || !alwaysSerializeErrorsAsList)) {
                    valueSerializer.serialize(encoder, list.get(0));
                } else {
                    listSerializer.serialize(encoder, list);
                }
            }
        }
        encoder.writeEndObject();
    }

    @Singleton
    static class Factory implements Serializer.Factory {
        private final JsonConfiguration jacksonConfiguration;

        @Inject
        Factory(JsonConfiguration jacksonConfiguration) {
            this.jacksonConfiguration = jacksonConfiguration;
        }

        @Override
        public Type getGenericType() {
            return GenericTypeFactory.makeParameterizedTypeWithOwner(null, OptionalMultiValues.class, OptionalMultiValuesSerializer.class.getTypeParameters()[0]);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public Serializer<? super OptionalMultiValues<?>> newInstance(SerializerLocator locator, Function<String, Type> getTypeParameter) {
            return new OptionalMultiValuesSerializer(
                    jacksonConfiguration,
                    locator.findContravariantSerializer(getTypeParameter.apply("V")),
                    locator.findContravariantSerializer(GenericTypeFactory.makeParameterizedTypeWithOwner(null, List.class, getTypeParameter.apply("V"))));
        }
    }
}
