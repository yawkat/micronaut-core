package io.micronaut.json.generated;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.util.ByteArrayBuilder;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.jackson.core.tree.MicronautTreeCodec;
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
import java.util.function.Consumer;

@Internal
@Singleton
@Secondary
@BootstrapContextCompatible
public final class GeneratedObjectCodec implements JsonCodec {
    private static final JsonFactory FACTORY = new JsonFactory();

    private final SerializerLocator locator;
    private final JsonConfig deserializationConfig;
    private final MicronautTreeCodec treeCodec;

    private GeneratedObjectCodec(SerializerLocator locator, JsonConfig deserializationConfig) {
        this.locator = locator;
        this.deserializationConfig = deserializationConfig;
        this.treeCodec = MicronautTreeCodec.getInstance().withConfig(deserializationConfig);
    }

    @Inject
    @Internal
    public GeneratedObjectCodec(SerializerLocator locator) {
        this(locator, JsonConfig.DEFAULT);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void writeValue0(JsonGenerator gen, Object value) throws IOException {
        writeValue0(gen, value, (Class) value.getClass());
    }

    // type-safe helper method
    private <T> void writeValue0(JsonGenerator gen, T value, Class<T> type) throws IOException {
        Serializer<? super T> serializer = locator.findContravariantSerializer(type);
        if (serializer instanceof ObjectSerializer) {
            throw new ObjectMappingException("No serializer for type " + type.getName());
        }
        serializer.serialize(gen, value);
    }

    private <T> T readValue(JsonParser parser, Argument<T> type) throws IOException {
        return readValue0(parser, GenericTypeUtils.argumentToReflectType(type));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> T readValue0(JsonParser parser, Type type) throws IOException {
        Deserializer deserializer = locator.findInvariantDeserializer(type);
        if (!parser.hasCurrentToken()) {
            parser.nextToken();
        }
        // for jackson compat we need to support deserializing null, but most deserializers don't support it.
        if (parser.currentToken() == JsonToken.VALUE_NULL && !deserializer.supportsNullDeserialization()) {
            return null;
        }
        return (T) deserializer.deserialize(parser);
    }

    @Override
    public <T> T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        try {
            return readValue(treeCodec.treeAsTokens(tree), type);
        } catch (JsonProcessingException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public JsonNode writeValueToTree(Object value) throws IOException {
        TreeGenerator treeGenerator = treeCodec.createTreeGenerator();
        writeValue0(treeGenerator, value);
        return treeGenerator.getCompletedValue();
    }

    @Override
    public <T> T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        return readValue(FACTORY.createParser(inputStream), type);
    }

    @Override
    public <T> T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        return readValue(FACTORY.createParser(byteArray), type);
    }

    @Override
    public void writeValue(OutputStream outputStream, Object object) throws IOException {
        writeValue0(FACTORY.createGenerator(outputStream), object);
    }

    @Override
    public byte[] writeValueAsBytes(Object object) throws IOException {
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

    @Override
    public JsonConfig getDeserializationConfig() {
        return deserializationConfig;
    }

    @Override
    public Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe, boolean streamArray) {
        return new JacksonCoreProcessor(streamArray, new JsonFactory(), deserializationConfig) {
            @Override
            public void subscribe(Subscriber<? super JsonNode> downstreamSubscriber) {
                onSubscribe.accept(this);
                super.subscribe(downstreamSubscriber);
            }
        };
    }
}
