/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.json.generated;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.type.ResolvedType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.jackson.core.tree.JsonNodeTreeCodec;
import io.micronaut.jackson.core.tree.TreeGenerator;
import io.micronaut.json.*;
import io.micronaut.json.generated.serializer.ObjectSerializer;
import io.micronaut.jackson.core.parser.JacksonCoreProcessor;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.function.Consumer;

@Internal
@Singleton
@Primary
@BootstrapContextCompatible
@DatabindChoice.RequiresGenerator
public final class GeneratedObjectMapper implements JsonMapper {
    private static final JsonFactory FACTORY = new JsonFactory();

    private final SerializerLocator locator;
    private final JsonStreamConfig deserializationConfig;
    private final JsonNodeTreeCodec treeCodec;
    private final ObjectCodecImpl objectCodecImpl = new ObjectCodecImpl();

    private GeneratedObjectMapper(SerializerLocator locator, JsonStreamConfig deserializationConfig) {
        this.locator = locator;
        this.deserializationConfig = deserializationConfig;
        this.treeCodec = JsonNodeTreeCodec.getInstance().withConfig(deserializationConfig);
    }

    @Inject
    @Internal
    public GeneratedObjectMapper(SerializerLocator locator) {
        this(locator, JsonStreamConfig.DEFAULT);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void writeValue0(JsonGenerator gen, Object value) throws IOException {
        writeValue0(gen, value, (Class) value.getClass());
    }

    // type-safe helper method
    private <T> void writeValue0(JsonGenerator gen, T value, Class<T> type) throws IOException {
        gen.setCodec(objectCodecImpl);
        Serializer<? super T> serializer = locator.findContravariantSerializer(type);
        if (serializer instanceof ObjectSerializer) {
            throw new ObjectMappingException("No serializer for type " + type.getName());
        }
        serializer.serialize(JacksonEncoder.create(gen), value);
    }

    private <T> T readValue(JsonParser parser, Argument<T> type) throws IOException {
        return readValue0(parser, GenericTypeUtils.argumentToReflectType(type));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> T readValue0(JsonParser parser, Type type) throws IOException {
        parser.setCodec(objectCodecImpl);
        Deserializer deserializer = locator.findInvariantDeserializer(type);
        if (!parser.hasCurrentToken()) {
            parser.nextToken();
        }
        // for jackson compat we need to support deserializing null, but most deserializers don't support it.
        if (parser.currentToken() == JsonToken.VALUE_NULL && !deserializer.supportsNullDeserialization()) {
            return null;
        }
        return (T) deserializer.deserialize(JacksonDecoder.create(parser));
    }

    @Override
    public <T> T readValueFromTree(@NonNull JsonNode tree, @NonNull Argument<T> type) throws IOException {
        try {
            return readValue(treeCodec.treeAsTokens(tree), type);
        } catch (JsonProcessingException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public @NonNull JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        TreeGenerator treeGenerator = treeCodec.createTreeGenerator();
        writeValue0(treeGenerator, value);
        return treeGenerator.getCompletedValue();
    }

    @Override
    public <T> T readValue(@NonNull InputStream inputStream, @NonNull Argument<T> type) throws IOException {
        return readValue(FACTORY.createParser(inputStream), type);
    }

    @Override
    public <T> T readValue(@NonNull byte[] byteArray, @NonNull Argument<T> type) throws IOException {
        return readValue(FACTORY.createParser(byteArray), type);
    }

    @Override
    public void writeValue(@NonNull OutputStream outputStream, @Nullable Object object) throws IOException {
        writeValue0(FACTORY.createGenerator(outputStream), object);
    }

    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        ByteArrayBuilder bb = new ByteArrayBuilder(FACTORY._getBufferRecycler());
        try (JsonGenerator generator = FACTORY.createGenerator(bb)) {
            writeValue0(generator, object);
        } catch (JsonProcessingException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        byte[] bytes = bb.toByteArray();
        bb.release();
        return bytes;
    }

    @NonNull
    @Override
    public JsonStreamConfig getStreamConfig() {
        return deserializationConfig;
    }

    @Override
    public @NonNull Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe, boolean streamArray) {
        return new JacksonCoreProcessor(streamArray, new JsonFactory(), deserializationConfig) {
            @Override
            public void subscribe(Subscriber<? super JsonNode> downstreamSubscriber) {
                onSubscribe.accept(this);
                super.subscribe(downstreamSubscriber);
            }
        };
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
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Iterator<T> readValues(JsonParser p, TypeReference<T> valueTypeRef) throws IOException {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTree(JsonGenerator gen, TreeNode tree) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public TreeNode createObjectNode() {
            throw new UnsupportedOperationException();
        }

        @Override
        public TreeNode createArrayNode() {
            throw new UnsupportedOperationException();
        }

        @Override
        public JsonParser treeAsTokens(TreeNode n) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T treeToValue(TreeNode n, Class<T> valueType) throws JsonProcessingException {
            throw new UnsupportedOperationException();
        }
    }
}
