package io.micronaut.json.generated;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;

public class ObjectMappingException extends JsonProcessingException {
    protected ObjectMappingException(String msg, JsonLocation loc, Throwable rootCause) {
        super(msg, loc, rootCause);
    }

    protected ObjectMappingException(String msg) {
        super(msg);
    }

    protected ObjectMappingException(String msg, JsonLocation loc) {
        super(msg, loc);
    }

    protected ObjectMappingException(String msg, Throwable rootCause) {
        super(msg, rootCause);
    }

    protected ObjectMappingException(Throwable rootCause) {
        super(rootCause);
    }
}
