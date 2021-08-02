package io.micronaut.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import io.micronaut.core.type.Argument;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

@Singleton
public class StubObjectCodec implements ExtendedObjectCodec {
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
        throw new UnsupportedEncodingException();
    }
}
