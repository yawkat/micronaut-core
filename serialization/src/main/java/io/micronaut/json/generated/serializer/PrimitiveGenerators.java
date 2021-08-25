package io.micronaut.json.generated.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.micronaut.core.reflect.GenericTypeFactory;
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
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.function.Function;

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
    private static abstract class Raw<T> implements Serializer<T>, Deserializer<T> {
        private final Serializer implSer;
        private final Deserializer implDes;

        Raw(SerializerLocator locator, Type genericType) {
            this.implSer = locator.findContravariantSerializer(genericType);
            this.implDes = locator.findInvariantDeserializer(genericType);
        }

        @SuppressWarnings("unchecked")
        @Override
        public T deserialize(JsonParser decoder) throws IOException {
            return (T) implDes.deserialize(decoder);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void serialize(JsonGenerator encoder, T value) throws IOException {
            implSer.serialize(encoder, value);
        }
    }

    @SuppressWarnings("rawtypes")
    @Singleton
    static class RawList extends Raw<List> {
        RawList(SerializerLocator locator) {
            super(locator, new GenericTypeToken<List<Object>>() {}.getType());
        }
    }

    @SuppressWarnings("rawtypes")
    @Singleton
    static class RawCollection extends Raw<Collection> {
        RawCollection(SerializerLocator locator) {
            super(locator, new GenericTypeToken<Collection<Object>>() {}.getType());
        }
    }

    @SuppressWarnings("rawtypes")
    @Singleton
    static class RawSet extends Raw<Set> {
        RawSet(SerializerLocator locator) {
            super(locator, new GenericTypeToken<Set<Object>>() {}.getType());
        }
    }

    @SuppressWarnings("rawtypes")
    @Singleton
    static class RawSortedSet extends Raw<SortedSet> {
        RawSortedSet(SerializerLocator locator) {
            super(locator, new GenericTypeToken<SortedSet<Object>>() {}.getType());
        }
    }

    @SuppressWarnings("rawtypes")
    @Singleton
    static class RawMap extends Raw<Map> {
        RawMap(SerializerLocator locator) {
            super(locator, new GenericTypeToken<Map<String, Object>>() {}.getType());
        }
    }

    @Singleton
    static class ObjectMapFactory implements Deserializer.Factory {
        private static final TypeVariable<?> typeParameter = Map.class.getTypeParameters()[1];

        @Override
        public Type getGenericType() {
            return GenericTypeFactory.makeParameterizedTypeWithOwner(null, Map.class, Object.class, typeParameter);
        }

        @Override
        public Deserializer<?> newInstance(SerializerLocator locator, Function<String, Type> getTypeParameter) {
            // find a deserializer for Map<String, V>
            Type actualType = GenericTypeFactory.makeParameterizedTypeWithOwner(null, Map.class, String.class, getTypeParameter.apply(typeParameter.getName()));
            return locator.findInvariantDeserializer(actualType);
        }
    }
}
