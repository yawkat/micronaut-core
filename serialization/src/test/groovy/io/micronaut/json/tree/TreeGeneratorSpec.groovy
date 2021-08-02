package io.micronaut.json.tree

import com.fasterxml.jackson.jr.stree.JrsArray
import com.fasterxml.jackson.jr.stree.JrsNumber
import com.fasterxml.jackson.jr.stree.JrsObject
import com.fasterxml.jackson.jr.stree.JrsString
import spock.lang.Specification

class TreeGeneratorSpec extends Specification {
    def scalar() {
        given:
        def gen = new TreeGenerator()

        when:
        gen.writeString("abc")

        then:
        gen.isComplete()
        gen.getCompletedValue() == new JrsString("abc")
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
        gen.getCompletedValue() == new JrsArray([new JrsString("abc"), new JrsNumber(123)])
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
        gen.getCompletedValue() == new JrsObject(["foo": new JrsString("abc"), "bar": new JrsNumber(123)])
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
        gen.getCompletedValue() == new JrsObject(["foo": new JrsObject(["bar": new JrsNumber(123)])])
    }
}
