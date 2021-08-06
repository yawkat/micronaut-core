package io.micronaut.json.generated;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.ResolvedType;
import com.fasterxml.jackson.core.type.TypeReference;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.json.GenericDeserializationConfig;
import io.micronaut.json.JsonFeatures;
import io.micronaut.json.MicronautObjectCodec;
import io.micronaut.json.Serializer;
import io.micronaut.json.tree.MicronautTreeCodec;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;

@Internal
@Singleton
@BootstrapContextCompatible
public final class GeneratedObjectCodec extends MicronautObjectCodec {
    private final BeanLocator locator;
    private final GenericDeserializationConfig deserializationConfig;
    private final MicronautTreeCodec treeCodec;

    private GeneratedObjectCodec(BeanLocator locator, GenericDeserializationConfig deserializationConfig) {
        this.locator = locator;
        this.deserializationConfig = deserializationConfig;
        this.treeCodec = MicronautTreeCodec.getInstance().withConfig(deserializationConfig);
    }

    @Inject
    GeneratedObjectCodec(BeanLocator locator) {
        this(locator, GenericDeserializationConfig.DEFAULT);
    }

    @SuppressWarnings("unchecked")
    private <T> Serializer<T> findSerializer(Argument<T> type) {
        return locator.getBean(Argument.of(Serializer.class, type));
    }

    @SuppressWarnings("unchecked")
    private <T> Serializer<T> findSerializer(Class<T> type) {
        return locator.getBean(Argument.of(Serializer.class, type));
    }

    private void moveToFirstToken(JsonParser parser) throws IOException {
        if (!parser.hasCurrentToken()) {
            parser.nextToken();
        }
    }

    @Override
    public ObjectCodec getObjectCodec() {
        return new ObjectCodecImpl();
    }

    @Override
    public <T> T readValue(JsonParser parser, Argument<T> type) throws IOException {
        moveToFirstToken(parser);
        return findSerializer(type).deserialize(parser);
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
            moveToFirstToken(p);
            return findSerializer(valueType).deserialize(p);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T readValue(JsonParser p, TypeReference<T> valueTypeRef) throws IOException {
            moveToFirstToken(p);
            return GeneratedObjectCodec.this.readValue(p, (Argument<T>) Argument.of(valueTypeRef.getType()));
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

        @SuppressWarnings("unchecked")
        @Override
        public void writeValue(JsonGenerator gen, Object value) throws IOException {
            ((Serializer<Object>) findSerializer(value.getClass())).serialize(gen, value);
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
            return deserializationConfig.getFactory();
        }
    }
}
