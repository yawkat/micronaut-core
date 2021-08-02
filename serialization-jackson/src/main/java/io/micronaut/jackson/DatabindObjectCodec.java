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
import io.micronaut.json.ExtendedObjectCodec;
import io.micronaut.json.GenericDeserializationConfig;
import io.micronaut.json.JsonFeatures;
import jakarta.inject.Singleton;

import java.io.IOException;

@Singleton
@BootstrapContextCompatible
public class DatabindObjectCodec implements ExtendedObjectCodec {
    private final ObjectMapper objectMapper;

    public DatabindObjectCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ObjectCodec getObjectCodec() {
        return objectMapper;
    }

    /*
    @Override
    public TreeNode valueToTree(Object value) {
        return objectMapper.valueToTree(value);
    }
    */

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
    public ExtendedObjectCodec cloneWithFeatures(JsonFeatures features) {
        JacksonFeatures jacksonFeatures = (JacksonFeatures) features;

        ObjectMapper objectMapper = this.objectMapper.copy();
        jacksonFeatures.getDeserializationFeatures().forEach(objectMapper::configure);
        jacksonFeatures.getSerializationFeatures().forEach(objectMapper::configure);

        return new DatabindObjectCodec(objectMapper);
    }

    @Override
    public ExtendedObjectCodec cloneWithViewClass(Class<?> viewClass) {
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
