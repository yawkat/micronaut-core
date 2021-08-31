package io.micronaut.json.tree

import spock.lang.Specification

class TreeGeneratorSpec extends Specification {
    def scalar() {
        given:
        def gen = new TreeGenerator()

        when:
        gen.writeString("abc")

        then:
        gen.isComplete()
        gen.getCompletedValue() == MicronautTreeCodec.getInstance().createStringNode("abc")
    }

    def array() {
        given:
        def gen = new TreeGenerator()

        when:
        gen.writeStartArray()
        gen.writeString("abc")
        gen.writeNumber(123)
        gen.writeEndArray()

        then:
        gen.isComplete()
        gen.getCompletedValue() == MicronautTreeCodec.getInstance().createArrayNode([
                MicronautTreeCodec.getInstance().createStringNode("abc"),
                MicronautTreeCodec.getInstance().createNumberNode(123)])
    }

    def object() {
        given:
        def gen = new TreeGenerator()

        when:
        gen.writeStartObject()
        gen.writeFieldName("foo")
        gen.writeString("abc")
        gen.writeFieldName("bar")
        gen.writeNumber(123)
        gen.writeEndObject()

        then:
        gen.isComplete()
        gen.getCompletedValue() == MicronautTreeCodec.getInstance().createObjectNode([
                "foo": MicronautTreeCodec.getInstance().createStringNode("abc"),
                "bar": MicronautTreeCodec.getInstance().createNumberNode(123)])
    }

    def nested() {
        given:
        def gen = new TreeGenerator()

        when:
        gen.writeStartObject()
        gen.writeFieldName("foo")
        gen.writeStartObject()
        gen.writeFieldName("bar")
        gen.writeNumber(123)
        gen.writeEndObject()
        gen.writeEndObject()

        then:
        gen.isComplete()
        gen.getCompletedValue() == MicronautTreeCodec.getInstance().createObjectNode([
                "foo": MicronautTreeCodec.getInstance().createObjectNode([
                        "bar": MicronautTreeCodec.getInstance().createNumberNode(123)])])
    }
}
