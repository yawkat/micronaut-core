package io.micronaut.jackson;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.jackson.annotation.JacksonFeatures;
import io.micronaut.json.codec.JsonMediaTypeCodec;
import io.micronaut.json.codec.JsonStreamMediaTypeCodec;
import io.micronaut.json.ExtendedObjectCodec;
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
    private final JsonMediaTypeCodec baseCodec;
    private final JsonStreamMediaTypeCodec streamCodec;

    @Inject
    public JacksonJsonAdapter(
            @Named("json") JsonMediaTypeCodec baseCodec,
            JsonStreamMediaTypeCodec streamCodec
    ) {
        this.baseCodec = baseCodec;
        this.streamCodec = streamCodec;
    }

    private JacksonJsonAdapter(ExtendedObjectCodec objectMapper, ApplicationConfiguration configuration) {
        this(
                new JsonMediaTypeCodec(objectMapper, configuration, null),
                new JsonStreamMediaTypeCodec(objectMapper, configuration, null)
        );
    }

    /**
     * For ServiceLoader (used where no app context is available)
     */
    public JacksonJsonAdapter() {
        this(new DatabindObjectCodec(new ObjectMapperFactory().objectMapper(null, null)), new ApplicationConfiguration());
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
    public GenericJsonMediaTypeCodec getJsonCodec() {
        return baseCodec;
    }

    @Override
    public GenericJsonMediaTypeCodec getStreamingJsonCodec() {
        return streamCodec;
    }
}
