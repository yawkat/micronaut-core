package io.micronaut.json.generated.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.json.JsonConfiguration;
import io.micronaut.json.Serializer;
import io.micronaut.json.generated.JsonParseException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Optional;

@Singleton
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
}
