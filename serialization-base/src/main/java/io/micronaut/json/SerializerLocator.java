package io.micronaut.json;

import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.GenericTypeToken;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@BootstrapContextCompatible
@Singleton
public final class SerializerLocator {
    private final BeanContext context;

    @Inject
    SerializerLocator(BeanContext context) {
        this.context = context;
    }

    public <T> Deserializer<T> findInvariantDeserializer(Type forType) {
        return this.<T>findInvariantDeserializerProvider(forType).get();
    }

    public <T> Deserializer<T> findInvariantDeserializer(Class<T> forType) {
        return findInvariantDeserializer((Type) forType);
    }

    public <T> Deserializer<T> findInvariantDeserializer(GenericTypeToken<T> typeToken) {
        return findInvariantDeserializer(typeToken.getType());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Provider<Deserializer<T>> findInvariantDeserializerProvider(Type forType) {
        Collection<BeanDefinition<Deserializer>> allDeserializers = context.getBeanDefinitions(Deserializer.class);
        for (BeanDefinition<? extends Deserializer> def : allDeserializers) {
            Type serializerType = getSerializerType(def, Deserializer.class);
            if (serializerType != null) {
                Map<TypeVariable<?>, Type> inferred = TypeInference.inferExact(serializerType, forType);
                if (inferred != null) {
                    if (inferred.isEmpty()) {
                        return () -> context.getBean(def);
                    } else {
                        return this.inferenceFactory((Class<? extends Deserializer<T>>) def.getBeanType(), inferred);
                    }
                }
            }
        }
        // TODO
        throw new NoSuchBeanException(forType.getTypeName()) {};
    }

    private <T> Provider<T> inferenceFactory(Class<? extends T> on, Map<TypeVariable<?>, Type> inferredTypes) {
        Constructor<?> constructor = Arrays.stream(on.getConstructors())
                .filter(c -> Arrays.stream(c.getParameterTypes()).allMatch(param -> param == Serializer.class || param == Deserializer.class || param == Provider.class))
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Missing constructor for generic serializer"));
        GenericTypeUtils.VariableFold fold = var -> {
            Type inferredType = inferredTypes.get(var);
            if (inferredType == null) {
                throw new IllegalArgumentException("Missing inferred variable " + var);
            }
            return inferredType;
        };
        List<Provider<?>> parameterProviders = Arrays.stream(constructor.getGenericParameterTypes())
                .map(t -> GenericTypeUtils.foldTypeVariables(t, fold))
                .map(this::requestParameter)
                .collect(Collectors.toList());
        return () -> {
            try {
                //noinspection unchecked
                return (T) constructor.newInstance(parameterProviders.stream().map(Provider::get).toArray(Object[]::new));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private Provider<?> requestParameter(Type type) {
        if (!(type instanceof ParameterizedType)) {
            throw new IllegalArgumentException("Must be parameterized type");
        }
        Type rawType = ((ParameterizedType) type).getRawType();
        if (rawType == Provider.class) {
            Provider<?> actualSupplier = requestParameter(((ParameterizedType) type).getActualTypeArguments()[0]);
            return () -> actualSupplier;
        } else if (rawType == Serializer.class) {
            Type parameter = ((ParameterizedType) type).getActualTypeArguments()[0];
            if (!(parameter instanceof WildcardType) || ((WildcardType) parameter).getLowerBounds().length != 1) {
                throw new IllegalStateException("Serializer must be contravariant wildcard");
            }
            return findContravariantSerializerProvider(((WildcardType) parameter).getLowerBounds()[0]);
        } else if (rawType == Deserializer.class) {
            Type parameter = ((ParameterizedType) type).getActualTypeArguments()[0];
            if (!(parameter instanceof WildcardType) || ((WildcardType) parameter).getLowerBounds().length != 0) {
                throw new IllegalStateException("Deserializer must be covariant wildcard");
            }
            // exact match, even though the param is covariant.
            return findInvariantDeserializerProvider(((WildcardType) parameter).getUpperBounds()[0]);
        } else {
            // checked above
            throw new AssertionError(rawType);
        }
    }

    public <T> Provider<Deserializer<T>> findInvariantDeserializerProvider(GenericTypeToken<T> typeToken) {
        return findInvariantDeserializerProvider(typeToken.getType());
    }

    public <T> Serializer<? super T> findContravariantSerializer(Type forType) {
        return findContravariantSerializerProvider(forType).get();
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
        Type parameterization = GenericTypeUtils.findParameterization(GenericTypeUtils.parameterizeWithFreeVariables(beanType.getBeanType()), target);
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

                    if (found != null && foundType != null && hereType != null && GenericTypeUtils.isAssignableFrom(hereType, foundType, true)) {
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
