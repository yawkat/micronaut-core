package io.micronaut.json.generator

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.reflect.GenericTypeToken
import io.micronaut.core.type.Argument
import io.micronaut.json.Serializer
import io.micronaut.json.SerializerLocator

class PrimitiveVisitorSpec extends AbstractTypeElementSpec {
    def test() {
        given:
        def ctx = buildContext('io.micronaut.json.generated.serializer.Test', '''
package io.micronaut.json.generated.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.micronaut.json.Serializer;
import java.lang.annotation.*;

@PrimitiveGenerators.GeneratePrimitiveSerializers
class PrimitiveGenerators {
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.SOURCE)
    @interface GeneratePrimitiveSerializers {
    }
}

@jakarta.inject.Singleton
class MockObjectSerializer implements Serializer<Object> {
    @Override
    public Object deserialize(JsonParser decoder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void serialize(JsonGenerator encoder, Object value) {
        throw new UnsupportedOperationException();
    }
}
''', true)
        def locator = ctx.getBean(SerializerLocator)

        expect:
        locator.findInvariantSerializer(Integer) != null
        locator.findInvariantSerializer(String) != null
        locator.findInvariantSerializer(new GenericTypeToken<List<Object>>() {}) != null
        locator.findInvariantSerializer(new GenericTypeToken<Map<String, Object>>() {}) != null
    }
}
