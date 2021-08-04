package io.micronaut.json.tree

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.core.JsonToken
import spock.lang.Specification

import static io.micronaut.json.tree.JsonScalarSpec.thrownException

class JsonObjectSpec extends Specification {
    def "object"() {
        given:
        def node = new JsonObject(['a': new JsonNumber(42), 'b': new JsonString('foo')])
        def valueIterator = node.valueIterator()
        def entryIterator = node.entryIterator()
        def fieldNames = node.fieldNames()

        expect:
        valueIterator.next() == new JsonNumber(42)
        valueIterator.next() == new JsonString('foo')
        !valueIterator.hasNext()

        def e1 = entryIterator.next()
        e1.key == 'a'
        e1.value == new JsonNumber(42)
        def e2 = entryIterator.next()
        e2.key == 'b'
        e2.value == new JsonString('foo')
        !entryIterator.hasNext()

        fieldNames.next() == 'a'
        fieldNames.next() == 'b'
        !fieldNames.hasNext()

        node.isContainerNode()
        !node.isArray()
        node.isObject()
        node.at(JsonPointer.empty()) == node
        node.at(JsonPointer.compile("/1")).isMissingNode()
        node.at(JsonPointer.compile("/a")) == new JsonNumber(42)
        node.at("") == node
        node.at("/1").isMissingNode()
        node.at("/a") == new JsonNumber(42)
        node.get("a") == new JsonNumber(42)
        node.get("bar") == null
        node.get(0) == null
        node.path("a") == new JsonNumber(42)
        node.path("bar").isMissingNode()
        node.path(0).isMissingNode()
        node.size() == 2
        !node.isValueNode()
        !node.isNumber()
        !node.isMissingNode()
        !node.isString()
        !node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        thrownException { node.coerceStringValue() } instanceof IllegalStateException
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        thrownException { node.getNumberValue() } instanceof IllegalStateException
        thrownException { node.getIntValue() } instanceof IllegalStateException
        thrownException { node.getLongValue() } instanceof IllegalStateException
        thrownException { node.getFloatValue() } instanceof IllegalStateException
        thrownException { node.getDoubleValue() } instanceof IllegalStateException
        thrownException { node.getBigIntegerValue() } instanceof IllegalStateException
        thrownException { node.getBigDecimalValue() } instanceof IllegalStateException
        node.asToken() == JsonToken.START_OBJECT
        node.numberType() == null
        node == new JsonObject(['a': new JsonNumber(42), 'b': new JsonString('foo')])
        node != new JsonObject(['a': new JsonNumber(43), 'b': new JsonString('foo')])
        node.hashCode() == new JsonObject(['a': new JsonNumber(42), 'b': new JsonString('foo')]).hashCode()
        node.toString() == '{"a":42,"b":"foo"}'
    }
}
