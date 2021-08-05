package io.micronaut.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import jakarta.inject.Singleton;

import java.io.IOException;

@Singleton
public class StubObjectCodec extends MicronautObjectCodec {
    @Override
    public ObjectCodec getObjectCodec() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T readValue(JsonParser parser, Argument<T> type) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateValue(JsonParser parser, Object value) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public MicronautObjectCodec cloneWithFeatures(JsonFeatures features) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MicronautObjectCodec cloneWithViewClass(Class<?> viewClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GenericDeserializationConfig getDeserializationConfig() {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public JsonFeatures detectFeatures(AnnotationMetadata annotations) {
        return null;
    }
}
