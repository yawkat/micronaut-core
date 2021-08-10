package io.micronaut.json.generated.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.micronaut.core.value.OptionalMultiValues;
import io.micronaut.core.value.OptionalValues;
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
class OptionalValuesSerializer<T extends OptionalValues<?>> implements Serializer<T> {
    private final boolean alwaysSerializeErrorsAsList;

    public OptionalValuesSerializer() {
        this.alwaysSerializeErrorsAsList = false;
    }

    @Inject
    public OptionalValuesSerializer(JsonConfiguration jacksonConfiguration) {
        this.alwaysSerializeErrorsAsList = jacksonConfiguration.isAlwaysSerializeErrorsAsList();
    }

    @Override
    public T deserialize(JsonParser decoder) throws IOException {
        // todo: is this right?
        throw JsonParseException.from(decoder, "Cannot deserialize OptionalValues");
    }

    @Override
    public void serialize(JsonGenerator encoder, T value) throws IOException {
        encoder.writeStartObject();
        for (CharSequence key : value) {
            Optional<?> opt = value.get(key);
            if (opt.isPresent()) {
                String fieldName = key.toString();
                encoder.writeFieldName(fieldName);
                Object v = opt.get();
                if (value instanceof OptionalMultiValues) {
                    List<?> list = (List<?>) v;

                    if (list.size() == 1 && (list.get(0).getClass() != JsonError.class || !alwaysSerializeErrorsAsList)) {
                        encoder.writeObject(list.get(0));
                    } else {
                        encoder.writeObject(list);
                    }
                } else {
                    encoder.writeObject(v);
                }
            }
        }
        encoder.writeEndObject();
    }
}
