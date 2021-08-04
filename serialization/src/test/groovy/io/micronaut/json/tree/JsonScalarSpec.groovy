package io.micronaut.json.tree

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.core.JsonToken
import spock.lang.Specification

class JsonScalarSpec extends Specification {
    static Exception thrownException(Closure<?> closure) {
        try {
            closure.run()
        } catch (Exception e) {
            return e;
        }
        throw new AssertionError();
    }

    def "common scalar methods"() {
        expect:
        thrownException { node.valueIterator() } instanceof IllegalStateException
        thrownException { node.entryIterator() } instanceof IllegalStateException
        !node.isContainerNode()
        !node.isArray()
        !node.isObject()
        node.at(JsonPointer.empty()) == node
        node.at(JsonPointer.compile("/abc")).isMissingNode()
        node.at("") == node
        node.at("/abc").isMissingNode()
        node.get("foo") == null
        node.get(0) == null
        node.path("foo").isMissingNode()
        node.path(0).isMissingNode()
        node.size() == 0
        !node.fieldNames().hasNext()

        where:
        node << [
                new JsonNumber(42),
                new JsonNumber(12345678901L),
                new JsonNumber(42.5F),
                new JsonNumber(12345678901.5D),
                new JsonNumber(new BigInteger("123456789012345678901234567890")),
                new JsonNumber(new BigDecimal("123456789012345678901234567890.5")),
                JsonNull.INSTANCE,
                JsonMissing.INSTANCE,
                JsonBoolean.valueOf(true),
                JsonBoolean.valueOf(false),
                new JsonString("foo")
        ]
    }

    def "int"() {
        given:
        def node = new JsonNumber(42)

        expect:
        node.isValueNode()
        node.isNumber()
        !node.isMissingNode()
        !node.isString()
        !node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        node.getNumberValue() == 42
        node.getIntValue() == 42
        node.getLongValue() == 42L
        node.getFloatValue() == 42.0F
        node.getDoubleValue() == 42.0D
        node.getBigIntegerValue() == BigInteger.valueOf(42)
        node.getBigDecimalValue() == BigDecimal.valueOf(42)
        node.asToken() == JsonToken.VALUE_NUMBER_INT
        node.numberType() == JsonParser.NumberType.INT
        node == new JsonNumber(42)
        node != new JsonNumber(43)
        node.hashCode() == new JsonNumber(42).hashCode()
        node.toString() == '42'
    }

    def "long"() {
        given:
        def node = new JsonNumber(12345678901L)

        expect:
        node.isValueNode()
        node.isNumber()
        !node.isMissingNode()
        !node.isString()
        !node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        node.getNumberValue() == 12345678901L
        node.getIntValue() == -539222987
        node.getLongValue() == 12345678901L
        node.getFloatValue() == 12345678901.0F
        node.getDoubleValue() == 12345678901.0D
        node.getBigIntegerValue() == BigInteger.valueOf(12345678901L)
        node.getBigDecimalValue() == BigDecimal.valueOf(12345678901L)
        node.asToken() == JsonToken.VALUE_NUMBER_INT
        node.numberType() == JsonParser.NumberType.LONG
        node == new JsonNumber(12345678901L)
        node != new JsonNumber(12345678902L)
        node.hashCode() == new JsonNumber(12345678901L).hashCode()
        node.toString() == '12345678901'
    }

    def "float"() {
        given:
        def node = new JsonNumber(42.5F)

        expect:
        node.isValueNode()
        node.isNumber()
        !node.isMissingNode()
        !node.isString()
        !node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        node.getNumberValue() == 42.5F
        node.getIntValue() == 42
        node.getLongValue() == 42L
        node.getFloatValue() == 42.5F
        node.getDoubleValue() == 42.5D
        node.getBigIntegerValue() == BigInteger.valueOf(42)
        node.getBigDecimalValue() == BigDecimal.valueOf(42.5)
        node.asToken() == JsonToken.VALUE_NUMBER_FLOAT
        node.numberType() == JsonParser.NumberType.FLOAT
        node == new JsonNumber(42.5F)
        node != new JsonNumber(42.6F)
        node.hashCode() == new JsonNumber(42.5F).hashCode()
        node.toString() == '42.5'
    }

    def "double"() {
        given:
        def node = new JsonNumber(12345678901.5D)

        expect:
        node.isValueNode()
        node.isNumber()
        !node.isMissingNode()
        !node.isString()
        !node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        node.getNumberValue() == 12345678901.5D
        node.getIntValue() == Integer.MAX_VALUE
        node.getLongValue() == 12345678901L
        node.getFloatValue() == 12345678800F
        node.getDoubleValue() == 12345678901.5D
        node.getBigIntegerValue() == BigInteger.valueOf(12345678901L)
        node.getBigDecimalValue() == BigDecimal.valueOf(12345678901.5D)
        node.asToken() == JsonToken.VALUE_NUMBER_FLOAT
        node.numberType() == JsonParser.NumberType.DOUBLE
        node == new JsonNumber(12345678901.5D)
        node != new JsonNumber(12345678901.6D)
        node.hashCode() == new JsonNumber(12345678901.5D).hashCode()
        node.toString() == '1.23456789015E10'
    }

    def "bigint"() {
        given:
        def node = new JsonNumber(new BigInteger("123456789012345678901234567890"))

        expect:
        node.isValueNode()
        node.isNumber()
        !node.isMissingNode()
        !node.isString()
        !node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        node.getNumberValue() == new BigInteger("123456789012345678901234567890")
        node.getIntValue() == 1312754386
        node.getLongValue() == -4362896299872285998
        node.getFloatValue() == 123456789012345678901234567890.0F
        node.getDoubleValue() == 123456789012345678901234567890.0D
        node.getBigIntegerValue() == new BigInteger("123456789012345678901234567890")
        node.getBigDecimalValue() == new BigDecimal("123456789012345678901234567890")
        node.asToken() == JsonToken.VALUE_NUMBER_INT
        node.numberType() == JsonParser.NumberType.BIG_INTEGER
        node == new JsonNumber(new BigInteger("123456789012345678901234567890"))
        node != new JsonNumber(new BigInteger("123456789012345678901234567891"))
        node.hashCode() == new JsonNumber(new BigInteger("123456789012345678901234567890")).hashCode()
        node.toString() == '123456789012345678901234567890'
    }

    def "bigdec"() {
        given:
        def node = new JsonNumber(new BigDecimal("123456789012345678901234567890.5"))

        expect:
        node.isValueNode()
        node.isNumber()
        !node.isMissingNode()
        !node.isString()
        !node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        node.getNumberValue() == new BigDecimal("123456789012345678901234567890.5")
        node.getIntValue() == 1312754386
        node.getLongValue() == -4362896299872285998
        node.getFloatValue() == 123456789012345678901234567890.0F // .5 doesn't fit, should be trimmed
        node.getDoubleValue() == 123456789012345678901234567890.0D // .5 doesn't fit, should be trimmed
        node.getBigIntegerValue() == new BigInteger("123456789012345678901234567890")
        node.getBigDecimalValue() == new BigDecimal("123456789012345678901234567890.5")
        node.asToken() == JsonToken.VALUE_NUMBER_FLOAT
        node.numberType() == JsonParser.NumberType.BIG_DECIMAL
        node == new JsonNumber(new BigDecimal("123456789012345678901234567890.5"))
        node != new JsonNumber(new BigDecimal("123456789012345678901234567890.6"))
        node.hashCode() == new JsonNumber(new BigDecimal("123456789012345678901234567890.5")).hashCode()
        node.toString() == '123456789012345678901234567890.5'
    }

    def "bool"() {
        given:
        def node = JsonBoolean.valueOf(true)

        expect:
        node.isValueNode()
        !node.isNumber()
        !node.isMissingNode()
        !node.isString()
        node.isBoolean()
        !node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        node.getBooleanValue()
        thrownException { node.getNumberValue() } instanceof IllegalStateException
        thrownException { node.getIntValue() } instanceof IllegalStateException
        thrownException { node.getLongValue() } instanceof IllegalStateException
        thrownException { node.getFloatValue() } instanceof IllegalStateException
        thrownException { node.getDoubleValue() } instanceof IllegalStateException
        thrownException { node.getBigIntegerValue() } instanceof IllegalStateException
        thrownException { node.getBigDecimalValue() } instanceof IllegalStateException
        node.asToken() == JsonToken.VALUE_TRUE
        node.numberType() == null
        node == JsonBoolean.valueOf(true)
        node != JsonBoolean.valueOf(false)
        node.hashCode() == JsonBoolean.valueOf(true).hashCode()
        node.toString() == 'true'
    }

    def "string"() {
        given:
        def node = new JsonString("foo")

        expect:
        node.isValueNode()
        !node.isNumber()
        !node.isMissingNode()
        node.isString()
        !node.isBoolean()
        !node.isNull()
        node.getStringValue() == 'foo'
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        thrownException { node.getNumberValue() } instanceof IllegalStateException
        thrownException { node.getIntValue() } instanceof IllegalStateException
        thrownException { node.getLongValue() } instanceof IllegalStateException
        thrownException { node.getFloatValue() } instanceof IllegalStateException
        thrownException { node.getDoubleValue() } instanceof IllegalStateException
        thrownException { node.getBigIntegerValue() } instanceof IllegalStateException
        thrownException { node.getBigDecimalValue() } instanceof IllegalStateException
        node.asToken() == JsonToken.VALUE_STRING
        node.numberType() == null
        node == new JsonString("foo")
        node != new JsonString("bar")
        node.hashCode() == new JsonString("foo").hashCode()
        node.toString() == '"foo"'
    }

    def "null"() {
        given:
        def node = JsonNull.INSTANCE

        expect:
        node.isValueNode()
        !node.isNumber()
        !node.isMissingNode()
        !node.isString()
        !node.isBoolean()
        node.isNull()
        thrownException { node.getStringValue() } instanceof IllegalStateException
        thrownException { node.getBooleanValue() } instanceof IllegalStateException
        thrownException { node.getNumberValue() } instanceof IllegalStateException
        thrownException { node.getIntValue() } instanceof IllegalStateException
        thrownException { node.getLongValue() } instanceof IllegalStateException
        thrownException { node.getFloatValue() } instanceof IllegalStateException
        thrownException { node.getDoubleValue() } instanceof IllegalStateException
        thrownException { node.getBigIntegerValue() } instanceof IllegalStateException
        thrownException { node.getBigDecimalValue() } instanceof IllegalStateException
        node.asToken() == JsonToken.VALUE_NULL
        node.numberType() == null
        node == JsonNull.INSTANCE
        node.hashCode() == JsonNull.INSTANCE.hashCode()
        node.toString() == 'null'
    }

    def "missing"() {
        given:
        def node = JsonMissing.INSTANCE

        expect:
        !node.isValueNode()
        !node.isNumber()
        node.isMissingNode()
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
        node.asToken() == JsonToken.NOT_AVAILABLE
        node.numberType() == null
        node == JsonMissing.INSTANCE
        node.hashCode() == JsonMissing.INSTANCE.hashCode()
        node.toString() == 'null'
    }


}
