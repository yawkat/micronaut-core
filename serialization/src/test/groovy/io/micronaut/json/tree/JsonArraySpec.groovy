package io.micronaut.json.tree

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.core.JsonToken
import spock.lang.Specification

import static io.micronaut.json.tree.JsonScalarSpec.thrownException

class JsonArraySpec extends Specification {
    def "array"() {
        given:
        def node = new JsonArray([new JsonNumber(42), new JsonString('foo')])
        def valueIterator = node.valueIterator()

        expect:
        valueIterator.next() == new JsonNumber(42)
        valueIterator.next() == new JsonString('foo')
        !valueIterator.hasNext()

        thrownException { node.entryIterator() } instanceof IllegalStateException
        node.isContainerNode()
        node.isArray()
        !node.isObject()
        node.at(JsonPointer.empty()) == node
        node.at(JsonPointer.compile("/1")) == new JsonString('foo')
        node.at(JsonPointer.compile("/abc")).isMissingNode()
        node.at("") == node
        node.at("/1") == new JsonString('foo')
        node.at("/abc").isMissingNode()
        node.get("foo") == null
        node.get(0) == new JsonNumber(42)
        node.get(-1) == null
        node.get(2) == null
        node.path("foo").isMissingNode()
        node.path(0) == new JsonNumber(42)
        node.path(2).isMissingNode()
        node.size() == 2
        !node.fieldNames().hasNext()
        !node.isValueNode()
        !node.isNumber()
        !node.isMissingNode()
        !node.isString()
        !node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        thrownException { node.getNumberValue() } instanceof IllegalStateException
        thrownException { node.getIntValue() } instanceof IllegalStateException
        thrownException { node.getLongValue() } instanceof IllegalStateException
        thrownException { node.getFloatValue() } instanceof IllegalStateException
        thrownException { node.getDoubleValue() } instanceof IllegalStateException
        thrownException { node.getBigIntegerValue() } instanceof IllegalStateException
        thrownException { node.getBigDecimalValue() } instanceof IllegalStateException
        node.asToken() == JsonToken.START_ARRAY
        node.numberType() == null
        node == new JsonArray([new JsonNumber(42), new JsonString('foo')])
        node != new JsonArray([new JsonNumber(43), new JsonString('foo')])
        node.hashCode() == new JsonArray([new JsonNumber(42), new JsonString('foo')]).hashCode()
        node.toString() == '[42,"foo"]'
    }
}
