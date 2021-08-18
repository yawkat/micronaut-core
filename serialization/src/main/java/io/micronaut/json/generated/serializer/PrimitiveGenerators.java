package io.micronaut.json.generated.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.micronaut.core.reflect.GenericTypeToken;
import io.micronaut.json.Deserializer;
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
    static class RawList implements Serializer<List>, Deserializer<List> {
        private final Serializer implSer;
        private final Deserializer implDes;

        RawList(SerializerLocator locator) {
            this.implSer = locator.findContravariantSerializer(new GenericTypeToken<List<Object>>() {});
            this.implDes = locator.findInvariantDeserializer(new GenericTypeToken<List<Object>>() {});
        }

        @Override
        public List deserialize(JsonParser decoder) throws IOException {
            return (List) implDes.deserialize(decoder);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void serialize(JsonGenerator encoder, List value) throws IOException {
            implSer.serialize(encoder, value);
        }
    }

    @SuppressWarnings("rawtypes")
    @Singleton
    static class RawMap implements Serializer<Map>, Deserializer<Map> {
        private final Serializer implSer;
        private final Deserializer implDes;

        RawMap(SerializerLocator locator) {
            this.implSer = locator.findContravariantSerializer(new GenericTypeToken<Map<String, Object>>() {});
            this.implDes = locator.findInvariantDeserializer(new GenericTypeToken<Map<String, Object>>() {});
        }

        @Override
        public Map deserialize(JsonParser decoder) throws IOException {
            return (Map) implDes.deserialize(decoder);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void serialize(JsonGenerator encoder, Map value) throws IOException {
            implSer.serialize(encoder, value);
        }
    }
}
