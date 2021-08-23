package io.micronaut.json.generated.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.micronaut.core.value.OptionalMultiValues;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.json.JsonConfiguration;
import io.micronaut.json.Serializer;
import io.micronaut.json.generated.JsonParseException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Singleton
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
}
