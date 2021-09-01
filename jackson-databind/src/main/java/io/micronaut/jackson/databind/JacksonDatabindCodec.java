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
package io.micronaut.jackson.databind;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.jackson.JacksonConfiguration;
import io.micronaut.jackson.core.tree.MicronautTreeCodec;
import io.micronaut.jackson.core.tree.TreeGenerator;
import io.micronaut.json.JsonConfig;
import io.micronaut.json.JsonCodec;
import io.micronaut.json.JsonFeatures;
import io.micronaut.jackson.core.parser.JacksonCoreProcessor;
import io.micronaut.json.tree.JsonNode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Internal
@Singleton
@BootstrapContextCompatible
public final class JacksonDatabindCodec implements JsonCodec {
    private final ObjectMapper objectMapper;
    private final JsonConfig config;
    private final MicronautTreeCodec treeCodec;

    @Inject
    @Internal
    public JacksonDatabindCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.config = JsonConfig.DEFAULT
                .withUseBigDecimalForFloats(objectMapper.getDeserializationConfig().isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS))
                .withUseBigIntegerForInts(objectMapper.getDeserializationConfig().isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS));
        this.treeCodec = MicronautTreeCodec.getInstance().withConfig(config);
    }

    @Internal
    public JacksonDatabindCodec() {
        this(new ObjectMapper());
    }

    @Internal
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public <T> T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        return objectMapper.readValue(treeCodec.treeAsTokens(tree), JacksonConfiguration.constructType(type, objectMapper.getTypeFactory()));
    }

    @Override
    public JsonNode writeValueToTree(Object value) throws IOException {
        TreeGenerator treeGenerator = treeCodec.createTreeGenerator();
        treeGenerator.setCodec(objectMapper);
        objectMapper.writeValue(treeGenerator, value);
        return treeGenerator.getCompletedValue();
    }

    @Override
    public <T> T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        return objectMapper.readValue(inputStream, JacksonConfiguration.constructType(type, objectMapper.getTypeFactory()));
    }

    @Override
    public <T> T readValue(byte[] byteArray, Argument<T> type) throws IOException {
        return objectMapper.readValue(byteArray, JacksonConfiguration.constructType(type, objectMapper.getTypeFactory()));
    }

    @Override
    public void writeValue(OutputStream outputStream, Object object) throws IOException {
        objectMapper.writeValue(outputStream, object);
    }

    @Override
    public byte[] writeValueAsBytes(Object object) throws IOException {
        return objectMapper.writeValueAsBytes(object);
    }

    @Override
    public void updateValueFromTree(Object value, JsonNode tree) throws IOException {
        objectMapper.readerForUpdating(value).readValue(treeCodec.treeAsTokens(tree));
    }

    @Override
    public JsonCodec cloneWithFeatures(JsonFeatures features) {
        JacksonFeatures jacksonFeatures = (JacksonFeatures) features;

        ObjectMapper objectMapper = this.objectMapper.copy();
        jacksonFeatures.getDeserializationFeatures().forEach(objectMapper::configure);
        jacksonFeatures.getSerializationFeatures().forEach(objectMapper::configure);

        return new JacksonDatabindCodec(objectMapper);
    }

    @Override
    public JsonCodec cloneWithViewClass(Class<?> viewClass) {
        ObjectMapper objectMapper = this.objectMapper.copy();
        objectMapper.setConfig(objectMapper.getSerializationConfig().withView(viewClass));
        objectMapper.setConfig(objectMapper.getDeserializationConfig().withView(viewClass));

        return new JacksonDatabindCodec(objectMapper);
    }

    @Override
    public JsonConfig getDeserializationConfig() {
        return config;
    }

    @Override
    public Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe, boolean streamArray) {
        return new JacksonCoreProcessor(streamArray, objectMapper.getFactory(), config) {
            @Override
            public void subscribe(Subscriber<? super JsonNode> downstreamSubscriber) {
                onSubscribe.accept(this);
                super.subscribe(downstreamSubscriber);
            }
        };
    }

    @Override
    public Optional<JsonFeatures> detectFeatures(AnnotationMetadata annotations) {
        return Optional.ofNullable(annotations.getAnnotation(io.micronaut.jackson.annotation.JacksonFeatures.class))
                .map(JacksonFeatures::fromAnnotation);
    }

    @Override
    public ConversionErrorException newConversionError(Object object, Exception e) {
        if (e instanceof InvalidFormatException) {
            InvalidFormatException ife = (InvalidFormatException) e;
            Object originalValue = ife.getValue();
            ConversionError conversionError = new ConversionError() {
                @Override
                public Exception getCause() {
                    return e;
                }

                @Override
                public Optional<Object> getOriginalValue() {
                    return Optional.ofNullable(originalValue);
                }
            };
            Class type = object != null ? object.getClass() : Object.class;
            List<JsonMappingException.Reference> path = ife.getPath();
            String name;
            if (!path.isEmpty()) {
                name = path.get(path.size() - 1).getFieldName();
            } else {
                name = NameUtils.decapitalize(type.getSimpleName());
            }
            return new ConversionErrorException(Argument.of(ife.getTargetType(), name), conversionError);
        }
        return JsonCodec.super.newConversionError(object, e);
    }
}
