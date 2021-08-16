package io.micronaut.json.generated;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.ResolvedType;
import com.fasterxml.jackson.core.type.TypeReference;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.json.*;
import io.micronaut.json.generated.serializer.ObjectSerializer;
import io.micronaut.json.tree.MicronautTreeCodec;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.util.Iterator;

@Internal
@Singleton
@Secondary
@BootstrapContextCompatible
public final class GeneratedObjectCodec extends MicronautObjectCodec {
    private final SerializerLocator locator;
    private final GenericDeserializationConfig deserializationConfig;
    private final MicronautTreeCodec treeCodec;

    private GeneratedObjectCodec(SerializerLocator locator, GenericDeserializationConfig deserializationConfig) {
        this.locator = locator;
        this.deserializationConfig = deserializationConfig;
        this.treeCodec = MicronautTreeCodec.getInstance().withConfig(deserializationConfig);
    }

    @Inject
    GeneratedObjectCodec(SerializerLocator locator) {
        this(locator, GenericDeserializationConfig.DEFAULT);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void writeValue0(JsonGenerator gen, Object value) throws IOException {
        writeValue0(gen, value, (Class) value.getClass());
    }

    // type-safe helper method
    private <T> void writeValue0(JsonGenerator gen, T value, Class<T> type) throws IOException {
        Serializer<? super T> serializer = locator.findContravariantSerializer(type);
        if (serializer instanceof ObjectSerializer) {
            // todo: custom exception
            throw new NoSuchBeanException("No serializer for type " + type.getName()) {};
        }
        serializer.serialize(gen, value);
    }

    @Override
    public <T> T readValue(JsonParser parser, Argument<T> type) throws IOException {
        return readValue0(parser, GenericTypeUtils.argumentToReflectType(type));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> T readValue0(JsonParser parser, Type type) throws IOException {
        Serializer serializer = locator.findInvariantSerializer(type);
        if (!parser.hasCurrentToken()) {
            parser.nextToken();
        }
        return (T) serializer.deserialize(parser);
    }

    @Override
    public ObjectCodec getObjectCodec() {
        return new ObjectCodecImpl();
    }

    @Override
    public void updateValue(JsonParser parser, Object value) throws IOException {
        throw new UnsupportedOperationException(); // TODO
    }

    @Override
    public MicronautObjectCodec cloneWithFeatures(JsonFeatures features) {
        throw new UnsupportedOperationException(); // TODO
    }

    @Override
    public MicronautObjectCodec cloneWithViewClass(Class<?> viewClass) {
        throw new UnsupportedOperationException(); // TODO
    }

    @Override
    public GenericDeserializationConfig getDeserializationConfig() {
        return deserializationConfig;
    }

    @Nullable
    @Override
    public JsonFeatures detectFeatures(AnnotationMetadata annotations) {
        return null;
    }

    private class ObjectCodecImpl extends ObjectCodec {
        @Override
        public Version version() {
            return Version.unknownVersion();
        }

        @Override
        public <T> T readValue(JsonParser p, Class<T> valueType) throws IOException {
            return readValue0(p, valueType);
        }

        @Override
        public <T> T readValue(JsonParser p, TypeReference<T> valueTypeRef) throws IOException {
            return readValue0(p, valueTypeRef.getType());
        }

        @Override
        public <T> T readValue(JsonParser p, ResolvedType valueType) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Iterator<T> readValues(JsonParser p, Class<T> valueType) throws IOException {
            throw new UnsupportedOperationException(); // TODO
        }

        @Override
        public <T> Iterator<T> readValues(JsonParser p, TypeReference<T> valueTypeRef) throws IOException {
            throw new UnsupportedOperationException(); // TODO
        }

        @Override
        public <T> Iterator<T> readValues(JsonParser p, ResolvedType valueType) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeValue(JsonGenerator gen, Object value) throws IOException {
            writeValue0(gen, value);
        }

        @Override
        public <T extends TreeNode> T readTree(JsonParser p) throws IOException {
            return treeCodec.readTree(p);
        }

        @Override
        public void writeTree(JsonGenerator gen, TreeNode tree) throws IOException {
            treeCodec.writeTree(gen, tree);
        }

        @Override
        public TreeNode createObjectNode() {
            return treeCodec.createObjectNode();
        }

        @Override
        public TreeNode createArrayNode() {
            return treeCodec.createArrayNode();
        }

        @Override
        public JsonParser treeAsTokens(TreeNode n) {
            return treeCodec.treeAsTokens(n);
        }

        @Override
        public <T> T treeToValue(TreeNode n, Class<T> valueType) throws JsonProcessingException {
            try {
                return readValue(treeAsTokens(n), valueType);
            } catch (JsonProcessingException e) {
                throw e;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public JsonFactory getFactory() {
            JsonFactory factory = deserializationConfig.getFactory();
            factory.setCodec(this);
            return factory;
        }
    }
}
