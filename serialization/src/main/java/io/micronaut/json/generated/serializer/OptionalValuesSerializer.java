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
class OptionalValuesSerializer implements Serializer<OptionalValues<?>> {
    private final boolean alwaysSerializeErrorsAsList;

    public OptionalValuesSerializer() {
        this.alwaysSerializeErrorsAsList = false;
    }

    @Inject
    public OptionalValuesSerializer(JsonConfiguration jacksonConfiguration) {
        this.alwaysSerializeErrorsAsList = jacksonConfiguration.isAlwaysSerializeErrorsAsList();
    }

    @Override
    public void serialize(JsonGenerator encoder, OptionalValues<?> value) throws IOException {
        encoder.writeStartObject();
        for (CharSequence key : value) {
            Optional<?> opt = value.get(key);
            if (opt.isPresent()) {
                encoder.writeFieldName(key.toString());
                encoder.writeObject(opt.get());
            }
        }
        encoder.writeEndObject();
    }
}
