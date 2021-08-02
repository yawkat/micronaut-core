package io.micronaut.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.type.Argument;
import io.micronaut.json.ExtendedObjectCodec;
import jakarta.inject.Singleton;

import java.io.IOException;

@Singleton
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
}
