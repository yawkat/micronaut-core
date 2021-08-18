package io.micronaut.json;

import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.GenericTypeToken;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.stream.Stream;

@BootstrapContextCompatible
@Singleton
public final class SerializerLocator {
    private final BeanContext context;

    @Inject
    SerializerLocator(BeanContext context) {
        this.context = context;
    }

    @SuppressWarnings({"unchecked"})
    public <T> Deserializer<T> findInvariantDeserializer(Type forType) {
        return context.getBean(Deserializer.class, new ExactMatchQualifier(forType));
    }

    public <T> Deserializer<T> findInvariantDeserializer(Class<T> forType) {
        return findInvariantDeserializer((Type) forType);
    }

    public <T> Deserializer<T> findInvariantDeserializer(GenericTypeToken<T> typeToken) {
        return findInvariantDeserializer(typeToken.getType());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Provider<Deserializer<T>> findInvariantDeserializerProvider(Type forType) {
        BeanDefinition<Deserializer> beanDefinition = context.getBeanDefinition(Deserializer.class, new ExactMatchQualifier(forType));
        return () -> context.getBean(beanDefinition);
    }

    public <T> Provider<Deserializer<T>> findInvariantDeserializerProvider(GenericTypeToken<T> typeToken) {
        return findInvariantDeserializerProvider(typeToken.getType());
    }

    @SuppressWarnings({"unchecked"})
    public <T> Serializer<? super T> findContravariantSerializer(Type forType) {
        return context.getBean(Serializer.class, new MostSpecificContravariantQualifier(forType));
    }

    public <T> Serializer<? super T> findContravariantSerializer(Class<T> forType) {
        return findContravariantSerializer((Type) forType);
    }

    public <T> Serializer<? super T> findContravariantSerializer(GenericTypeToken<T> typeToken) {
        return findContravariantSerializer(typeToken.getType());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Provider<Serializer<? super T>> findContravariantSerializerProvider(Type forType) {
        BeanDefinition<Serializer> beanDefinition = context.getBeanDefinition(Serializer.class, new MostSpecificContravariantQualifier(forType));
        return () -> context.getBean(beanDefinition);
    }

    public <T> Provider<Serializer<? super T>> findContravariantSerializerProvider(GenericTypeToken<T> typeToken) {
        return findContravariantSerializerProvider(typeToken.getType());
    }

    @Nullable
    private static <T> Type getSerializerType(BeanType<? extends T> beanType, Class<T> target) {
        Type parameterization = GenericTypeUtils.findParameterization(beanType.getBeanType(), target);
        if (parameterization instanceof ParameterizedType) {
            return ((ParameterizedType) parameterization).getActualTypeArguments()[0];
        } else {
            // raw type, would be eligible for any serializer
            return null;
        }
    }

    @SuppressWarnings("rawtypes")
    private static class ExactMatchQualifier implements Qualifier<Deserializer> {
        private final Type type;

        ExactMatchQualifier(Type type) {
            this.type = type;
        }

        @Override
        public <BT extends BeanType<Deserializer>> Stream<BT> reduce(Class<Deserializer> beanType, Stream<BT> candidates) {
            return candidates.filter(candidate -> GenericTypeUtils.typesEqual(getSerializerType(candidate, Deserializer.class), type));
        }

        @Override
        public String toString() {
            return "ExactMatchQualifier{" +
                    "type=" + type +
                    '}';
        }
    }

    // note: JLS §4.10 notation:
    // S :> T       S is a supertype of T, T is assignable to S
    // S > T        S :> T and S != T

    /**
     * Qualifier for finding the most specific serializer that can serialize a given type.
     * {@code serializerType :> type} holds. Raw types are treated as {@link Object}. Behavior when there are multiple
     * most specific serializers is implementation-defined.
     */
    @SuppressWarnings("rawtypes")
    private static class MostSpecificContravariantQualifier implements Qualifier<Serializer> {
        private final Type type;

        MostSpecificContravariantQualifier(Type type) {
            this.type = type;
        }

        @Override
        public <BT extends BeanType<Serializer>> Stream<BT> reduce(Class<Serializer> beanType, Stream<BT> candidates) {
            BT found = null;
            Type foundType = null;
            for (Iterator<BT> itr = candidates.iterator(); itr.hasNext(); ) {
                BT here = itr.next();
                // the type that this BeanType can serialize, or null if this is a raw class
                Type hereType = getSerializerType(here, Serializer.class);
                if (hereType == null || GenericTypeUtils.isAssignableFrom(hereType, type)) {
                    // hereType :> type

                    if (found != null && foundType != null && hereType != null && GenericTypeUtils.isAssignableFrom(hereType, foundType)) {
                        // hereType :> foundType :> type, foundType is the better choice
                        break;
                    }

                    found = here;
                    foundType = hereType;
                }
            }
            if (found == null) {
                return Stream.empty();
            } else {
                return Stream.of(found);
            }
        }

        @Override
        public String toString() {
            return "MostSpecificContravariantQualifier{" +
                    "type=" + type +
                    '}';
        }
    }
}
