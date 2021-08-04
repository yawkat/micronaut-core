package io.micronaut.jackson.convert;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micronaut.core.convert.ConversionService;

@Deprecated
public class ObjectNodeConvertibleValues<V> extends io.micronaut.json.convert.ObjectNodeConvertibleValues<V> {
    public ObjectNodeConvertibleValues(ObjectNode objectNode, ConversionService<?> conversionService) {
        super(objectNode, conversionService);
    }
}
