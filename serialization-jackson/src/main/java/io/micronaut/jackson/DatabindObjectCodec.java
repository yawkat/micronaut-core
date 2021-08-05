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
package io.micronaut.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.type.Argument;
import io.micronaut.jackson.codec.JacksonFeatures;
import io.micronaut.json.MicronautObjectCodec;
import io.micronaut.json.GenericDeserializationConfig;
import io.micronaut.json.JsonFeatures;
import jakarta.inject.Singleton;

import java.io.IOException;

@Singleton
@BootstrapContextCompatible
public final class DatabindObjectCodec extends MicronautObjectCodec {
    private final ObjectMapper objectMapper;

    public DatabindObjectCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ObjectCodec getObjectCodec() {
        return objectMapper;
    }

    @Override
    public <T> T readValue(JsonParser parser, Argument<T> type) throws IOException {
        TypeFactory typeFactory = objectMapper.getTypeFactory();
        JavaType javaType = JacksonConfiguration.constructType(type, typeFactory);
        return objectMapper.readValue(parser, javaType);
    }

    @Override
    public void updateValue(JsonParser parser, Object value) throws IOException {
        objectMapper.readerForUpdating(value).readValue(parser);
    }

    @Override
    public MicronautObjectCodec cloneWithFeatures(JsonFeatures features) {
        JacksonFeatures jacksonFeatures = (JacksonFeatures) features;

        ObjectMapper objectMapper = this.objectMapper.copy();
        jacksonFeatures.getDeserializationFeatures().forEach(objectMapper::configure);
        jacksonFeatures.getSerializationFeatures().forEach(objectMapper::configure);

        return new DatabindObjectCodec(objectMapper);
    }

    @Override
    public MicronautObjectCodec cloneWithViewClass(Class<?> viewClass) {
        ObjectMapper objectMapper = this.objectMapper.copy();
        objectMapper.setConfig(objectMapper.getSerializationConfig().withView(viewClass));
        // todo: JsonViewMediaTypeCodecFactory doesn't set the deser config, is doing this an issue?
        objectMapper.setConfig(objectMapper.getDeserializationConfig().withView(viewClass));

        return new DatabindObjectCodec(objectMapper);
    }

    @Override
    public GenericDeserializationConfig getDeserializationConfig() {
        return DatabindUtil.toGenericConfig(objectMapper);
    }

    @Override
    public JsonFeatures detectFeatures(AnnotationMetadata annotations) {
        AnnotationValue<io.micronaut.jackson.annotation.JacksonFeatures> annotationValue = annotations.getAnnotation(io.micronaut.jackson.annotation.JacksonFeatures.class);
        if (annotationValue != null) {
            return io.micronaut.jackson.codec.JacksonFeatures.fromAnnotation(annotationValue);
        } else {
            return null;
        }
    }
}
