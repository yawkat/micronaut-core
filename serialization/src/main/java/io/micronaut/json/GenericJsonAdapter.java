package io.micronaut.json;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.codec.JacksonMediaTypeCodec;

import java.util.ServiceLoader;

// todo: would be nice to remove this
@Internal
public interface GenericJsonAdapter {
    static GenericJsonAdapter getUnboundInstance() {
        return Helper.instance;
    }

    ExtendedObjectCodec createObjectMapper();
}

class Helper {
    static final GenericJsonAdapter instance = ServiceLoader.load(GenericJsonAdapter.class).iterator().next();
}
