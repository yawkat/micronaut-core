package io.micronaut.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.codec.CodecConfiguration;
import io.micronaut.jackson.annotation.JacksonFeatures;
import io.micronaut.jackson.codec.JsonMediaTypeCodec;
import io.micronaut.jackson.codec.JsonStreamMediaTypeCodec;
import io.micronaut.json.GenericJsonAdapter;
import io.micronaut.json.GenericJsonMediaTypeCodec;
import io.micronaut.json.JsonFeatures;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@BootstrapContextCompatible
public class JacksonJsonAdapter implements GenericJsonAdapter {
    private final ApplicationConfiguration applicationConfiguration;
    @Nullable
    private final CodecConfiguration codecConfiguration;

    @Inject
    public JacksonJsonAdapter(
            ApplicationConfiguration applicationConfiguration,
            @Named(JsonMediaTypeCodec.CONFIGURATION_QUALIFIER)
            @Nullable CodecConfiguration codecConfiguration
    ) {
        this.applicationConfiguration = applicationConfiguration;
        this.codecConfiguration = codecConfiguration;
    }

    /**
     * For ServiceLoader (used where no app context is available)
     */
    public JacksonJsonAdapter() {
        this(new ApplicationConfiguration(), null);
    }

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
    public GenericJsonMediaTypeCodec createNewJsonCodec() {
        ObjectMapper objectMapper = new ObjectMapperFactory().objectMapper(null, null);
        return new JsonMediaTypeCodec(objectMapper, applicationConfiguration, codecConfiguration);
    }

    @Override
    public GenericJsonMediaTypeCodec createNewStreamingJsonCodec() {
        ObjectMapper objectMapper = new ObjectMapperFactory().objectMapper(null, null);
        return new JsonStreamMediaTypeCodec(objectMapper, applicationConfiguration, codecConfiguration);
    }
}
