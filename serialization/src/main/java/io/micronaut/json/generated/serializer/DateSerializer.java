package io.micronaut.json.generated.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.micronaut.json.Deserializer;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Date;

@Singleton
class DateSerializer implements Serializer<Date>, Deserializer<Date> {
    private final Serializer<? super Long> longSerializer;
    private final Deserializer<? extends Long> longDeserializer;

    DateSerializer(SerializerLocator locator) {
        this.longSerializer = locator.findContravariantSerializer(long.class);
        this.longDeserializer = locator.findInvariantDeserializer(long.class);
    }

    @Override
    public Date deserialize(JsonParser decoder) throws IOException {
        return new Date(longDeserializer.deserialize(decoder));
    }

    @Override
    public void serialize(JsonGenerator encoder, Date value) throws IOException {
        longSerializer.serialize(encoder, value.getTime());
    }
}
