package io.micronaut.json.generated.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.micronaut.core.reflect.GenericTypeToken;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

/**
 * Marker class to tell the generator that we should generate the primitive serializers here.
 */
@PrimitiveGenerators.GeneratePrimitiveSerializers
class PrimitiveGenerators {
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.SOURCE)
    @interface GeneratePrimitiveSerializers {
    }

    // serializers for raw types

    @SuppressWarnings("rawtypes")
    @Singleton
    static class RawList implements Serializer<List> {
        private final Serializer<List<Object>> impl;

        RawList(SerializerLocator locator) {
            this.impl = locator.findInvariantSerializer(new GenericTypeToken<List<Object>>() {});
        }

        @Override
        public List deserialize(JsonParser decoder) throws IOException {
            return impl.deserialize(decoder);
        }

        @Override
        public void serialize(JsonGenerator encoder, List value) throws IOException {
            //noinspection unchecked
            impl.serialize(encoder, value);
        }
    }

    @SuppressWarnings("rawtypes")
    @Singleton
    static class RawMap implements Serializer<Map> {
        private final Serializer<Map<String, Object>> impl;

        RawMap(SerializerLocator locator) {
            this.impl = locator.findInvariantSerializer(new GenericTypeToken<Map<String, Object>>() {});
        }

        @Override
        public Map deserialize(JsonParser decoder) throws IOException {
            return impl.deserialize(decoder);
        }

        @Override
        public void serialize(JsonGenerator encoder, Map value) throws IOException {
            //noinspection unchecked
            impl.serialize(encoder, value);
        }
    }
}
