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
class OptionalMultiValuesSerializer implements Serializer<OptionalMultiValues<?>> {
    private final boolean alwaysSerializeErrorsAsList;

    public OptionalMultiValuesSerializer() {
        this.alwaysSerializeErrorsAsList = false;
    }

    @Inject
    public OptionalMultiValuesSerializer(JsonConfiguration jacksonConfiguration) {
        this.alwaysSerializeErrorsAsList = jacksonConfiguration.isAlwaysSerializeErrorsAsList();
    }

    @Override
    public void serialize(JsonGenerator encoder, OptionalMultiValues<?> value) throws IOException {
        encoder.writeStartObject();
        for (CharSequence key : value) {
            Optional<? extends List<?>> opt = value.get(key);
            if (opt.isPresent()) {
                String fieldName = key.toString();
                encoder.writeFieldName(fieldName);
                List<?> list = opt.get();
                if (list.size() == 1 && (list.get(0).getClass() != JsonError.class || !alwaysSerializeErrorsAsList)) {
                    encoder.writeObject(list.get(0));
                } else {
                    encoder.writeObject(list);
                }
            }
        }
        encoder.writeEndObject();
    }
}
