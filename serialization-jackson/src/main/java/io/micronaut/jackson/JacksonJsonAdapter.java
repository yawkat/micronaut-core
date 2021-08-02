package io.micronaut.jackson;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.jackson.annotation.JacksonFeatures;
import io.micronaut.json.codec.JacksonMediaTypeCodec;
import io.micronaut.json.codec.JsonMediaTypeCodec;
import io.micronaut.json.codec.JsonStreamMediaTypeCodec;
import io.micronaut.json.ExtendedObjectCodec;
import io.micronaut.json.GenericJsonAdapter;
import io.micronaut.json.JsonFeatures;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * For ServiceLoader (used where no app context is available)
 */
public class JacksonJsonAdapter implements GenericJsonAdapter {
    @Nullable
    @Override
    public JsonFeatures detectFeatures(AnnotationMetadata annotations) {
        AnnotationValue<JacksonFeatures> annotationValue = annotations.getAnnotation(JacksonFeatures.class);
        if (annotationValue != null) {
            return io.micronaut.jackson.codec.JacksonFeatures.fromAnnotation(annotationValue);
        } else {
            return null;
        }
    }

    @Override
    public ExtendedObjectCodec createObjectMapper() {
        return new DatabindObjectCodec(new ObjectMapperFactory().objectMapper(null, null));
    }
}
