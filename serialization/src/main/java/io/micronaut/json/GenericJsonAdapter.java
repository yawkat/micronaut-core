package io.micronaut.json;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.util.ServiceLoader;

@Internal
public interface GenericJsonAdapter {
    static GenericJsonAdapter getUnboundInstance() {
        return Helper.instance;
    }

    @Nullable
    JsonFeatures detectFeatures(AnnotationMetadata annotations);

    GenericJsonMediaTypeCodec createNewJsonCodec();

    GenericJsonMediaTypeCodec createNewStreamingJsonCodec();
}

class Helper {
    static final GenericJsonAdapter instance = ServiceLoader.load(GenericJsonAdapter.class).iterator().next();
}
