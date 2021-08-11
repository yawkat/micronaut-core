package io.micronaut.json.generator

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.type.Argument
import io.micronaut.json.Serializer

class PrimitiveVisitorSpec extends AbstractTypeElementSpec {
    def test() {
        given:
        def ctx = buildContext('''
package example;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.micronaut.json.Serializer;

@io.micronaut.json.annotation.$GeneratePrimitiveSerializers
class Marker {}

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
''')

        expect:
        ctx.getBean(Argument.of(Serializer, Integer)) != null
        ctx.getBean(Argument.of(Serializer, String)) != null
        ctx.getBean(Argument.of(Serializer, Argument.of(List, Object))) != null
        ctx.getBean(Argument.of(Serializer, Argument.of(Map, String, Object))) != null
    }
}
