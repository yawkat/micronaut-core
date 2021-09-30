package io.micronaut.json.generated.serializer;

import io.micronaut.json.Encoder;
import io.micronaut.json.GenericTypeFactory;
import io.micronaut.json.Serializer;
import io.micronaut.json.SerializerLocator;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.function.Function;
import java.util.stream.Stream;

final class StreamSerializer<T> implements Serializer<Stream<T>> {
    private final Serializer<T> valueSerializer;

    private StreamSerializer(Serializer<T> valueSerializer) {
        this.valueSerializer = valueSerializer;
    }

    @Override
    public void serialize(Encoder encoder, Stream<T> value) throws IOException {
        Encoder arrayEncoder = encoder.encodeArray();
        Iterator<T> itr = value.iterator();
        while (itr.hasNext()) {
            valueSerializer.serialize(arrayEncoder, itr.next());
        }
        arrayEncoder.finishStructure();
    }

    @Singleton
    static final class Factory implements Serializer.Factory {
        @Override
        public Type getGenericType() {
            return GenericTypeFactory.makeParameterizedTypeWithOwner(null, Stream.class, StreamSerializer.class.getTypeParameters()[0]);
        }

        @Override
        public Serializer<?> newInstance(SerializerLocator locator, Function<String, Type> getTypeParameter) {
            Serializer<?> valueSerializer = locator.findContravariantSerializer(getTypeParameter.apply("T"));
            return new StreamSerializer<>(valueSerializer);
        }
    }

    @Singleton
    static final class RawFactory implements Serializer.Factory {
        @Override
        public Type getGenericType() {
            return Stream.class;
        }

        @Override
        public Serializer<?> newInstance(SerializerLocator locator, Function<String, Type> getTypeParameter) {
            Serializer<?> valueSerializer = locator.findContravariantSerializer(Object.class);
            return new StreamSerializer<>(valueSerializer);
        }
    }
}
