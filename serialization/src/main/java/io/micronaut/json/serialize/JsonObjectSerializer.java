package io.micronaut.json.serialize;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micronaut.core.serialize.ObjectSerializer;
import io.micronaut.core.serialize.exceptions.SerializationException;
import io.micronaut.core.type.Argument;
import io.micronaut.json.MicronautObjectCodec;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

@Singleton
public final class JsonObjectSerializer implements ObjectSerializer {

    private final MicronautObjectCodec codec;

    /**
     * @param codec To read/write JSON
     */
    public JsonObjectSerializer(MicronautObjectCodec codec) {
        this.codec = codec;
    }

    @Override
    public Optional<byte[]> serialize(Object object) throws SerializationException {
        try {
            return Optional.ofNullable(codec.writeValueAsBytes(object));
        } catch (JsonProcessingException e) {
            throw new SerializationException("Error serializing object to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public void serialize(Object object, OutputStream outputStream) throws SerializationException {
        try {
            codec.getObjectCodec().writeValue(codec.getObjectCodec().getFactory().createGenerator(outputStream), object);
        } catch (IOException e) {
            throw new SerializationException("Error serializing object to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> Optional<T> deserialize(byte[] bytes, Class<T> requiredType) throws SerializationException {
        try {
            return Optional.ofNullable(codec.getObjectCodec().readValue(codec.getObjectCodec().getFactory().createParser(bytes), requiredType));
        } catch (IOException e) {
            throw new SerializationException("Error deserializing object from JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> Optional<T> deserialize(InputStream inputStream, Class<T> requiredType) throws SerializationException {
        try {
            return Optional.ofNullable(codec.getObjectCodec().readValue(codec.getObjectCodec().getFactory().createParser(inputStream), requiredType));
        } catch (IOException e) {
            throw new SerializationException("Error deserializing object from JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> Optional<T> deserialize(byte[] bytes, Argument<T> requiredType) throws SerializationException {
        try {
            return Optional.ofNullable(codec.readValue(codec.getObjectCodec().getFactory().createParser(bytes), requiredType));
        } catch (IOException e) {
            throw new SerializationException("Error deserializing object from JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public <T> Optional<T> deserialize(InputStream inputStream, Argument<T> requiredType) throws SerializationException {
        try {
            return Optional.ofNullable(codec.readValue(codec.getObjectCodec().getFactory().createParser(inputStream), requiredType));
        } catch (IOException e) {
            throw new SerializationException("Error deserializing object from JSON: " + e.getMessage(), e);
        }
    }
}
