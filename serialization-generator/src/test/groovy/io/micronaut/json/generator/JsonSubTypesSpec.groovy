package io.micronaut.json.generator

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.json.Deserializer
import io.micronaut.json.Serializer

class JsonSubTypesSpec extends AbstractTypeElementSpec implements SerializerUtils {
    def 'wrapper array'() {
        given:
        def compiled = buildClassLoader('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class, name = "a"),
    @JsonSubTypes.Type(value = B.class, names = {"b", "c"})
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_ARRAY)
class Base {
}

class A extends Base {
    String fieldA;
}
class B extends Base {
    String fieldB;
}
''')
        def deserializer = (Deserializer) compiled.loadClass('example.$Base$Deserializer').newInstance()

        def serializer = (Serializer) compiled.loadClass('example.$Base$Serializer').newInstance()
        def a = compiled.loadClass('example.A').newInstance()
        a.fieldA = 'foo'

        expect:
        deserializeFromString(deserializer, '["a",{"fieldA":"foo"}]').fieldA == 'foo'
        deserializeFromString(deserializer, '["b",{"fieldB":"foo"}]').fieldB == 'foo'
        deserializeFromString(deserializer, '["c",{"fieldB":"foo"}]').fieldB == 'foo'

        serializeToString(serializer, a) == '["a",{"fieldA":"foo"}]'
    }

    def 'wrapper object'() {
        given:
        def compiled = buildClassLoader('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class, name = "a"),
    @JsonSubTypes.Type(value = B.class, names = {"b", "c"})
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
class Base {
}

class A extends Base {
    String fieldA;
}
class B extends Base {
    String fieldB;
}
''')
        def deserializer = (Deserializer) compiled.loadClass('example.$Base$Deserializer').newInstance()

        def serializer = (Serializer) compiled.loadClass('example.$Base$Serializer').newInstance()
        def a = compiled.loadClass('example.A').newInstance()
        a.fieldA = 'foo'

        expect:
        deserializeFromString(deserializer, '{"a":{"fieldA":"foo"}}').fieldA == 'foo'
        deserializeFromString(deserializer, '{"b":{"fieldB":"foo"}}').fieldB == 'foo'
        deserializeFromString(deserializer, '{"c":{"fieldB":"foo"}}').fieldB == 'foo'

        serializeToString(serializer, a) == '{"a":{"fieldA":"foo"}}'
    }

    def 'property'() {
        given:
        def compiled = buildClassLoader('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class, name = "a"),
    @JsonSubTypes.Type(value = B.class, names = {"b", "c"})
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
class Base {
}

class A extends Base {
    String fieldA;
}
class B extends Base {
    String fieldB;
}
''')
        def deserializer = (Deserializer) compiled.loadClass('example.$Base$Deserializer').newInstance()

        def serializer = (Serializer) compiled.loadClass('example.$Base$Serializer').newInstance()
        def a = compiled.loadClass('example.A').newInstance()
        a.fieldA = 'foo'

        expect:
        deserializeFromString(deserializer, '{"type":"a","fieldA":"foo"}').fieldA == 'foo'
        deserializeFromString(deserializer, '{"type":"b","fieldB":"foo"}').fieldB == 'foo'
        deserializeFromString(deserializer, '{"type":"c","fieldB":"foo"}').fieldB == 'foo'

        serializeToString(serializer, a) == '{"type":"a","fieldA":"foo"}'
    }

    def 'deduction'() {
        given:
        def compiled = buildClassLoader('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class),
    @JsonSubTypes.Type(value = B.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
class Base {
}

class A extends Base {
    String fieldA;
}
class B extends Base {
    String fieldB;
}
''')
        def deserializer = (Deserializer) compiled.loadClass('example.$Base$Deserializer').newInstance()

        def serializer = (Serializer) compiled.loadClass('example.$Base$Serializer').newInstance()
        def a = compiled.loadClass('example.A').newInstance()
        a.fieldA = 'foo'

        expect:
        deserializeFromString(deserializer, '{"fieldA":"foo"}').fieldA == 'foo'
        deserializeFromString(deserializer, '{"fieldB":"foo"}').fieldB == 'foo'

        serializeToString(serializer, a) == '{"fieldA":"foo"}'
    }

    def 'deduction with supertype prop'() {
        given:
        def compiled = buildClassLoader('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
@JsonSubTypes({
    @JsonSubTypes.Type(value = A.class),
    @JsonSubTypes.Type(value = B.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
class Base {
    String sup;
}

class A extends Base {
    String fieldA;
}
class B extends Base {
    String fieldB;
}
''')
        def deserializer = (Deserializer) compiled.loadClass('example.$Base$Deserializer').newInstance()

        def serializer = (Serializer) compiled.loadClass('example.$Base$Serializer').newInstance()
        def a = compiled.loadClass('example.A').newInstance()
        a.sup = 'x'
        a.fieldA = 'foo'

        expect:
        deserializeFromString(deserializer, '{"sup":"x","fieldA":"foo"}').sup == 'x'
        deserializeFromString(deserializer, '{"sup":"x","fieldA":"foo"}').fieldA == 'foo'
        deserializeFromString(deserializer, '{"sup":"x","fieldB":"foo"}').sup == 'x'
        deserializeFromString(deserializer, '{"sup":"x","fieldB":"foo"}').fieldB == 'foo'

        serializeToString(serializer, a) == '{"fieldA":"foo","sup":"x"}'
    }

    def 'deduction unwrapped'() {
        given:
        def compiled = buildClassLoader('example.Base', '''
package example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
@JsonSubTypes({
    @JsonSubTypes.Type(value = A1.class),
    @JsonSubTypes.Type(value = B1.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
class Base1 {
    @JsonUnwrapped Base2 base2;
}

class A1 extends Base1 {
    String fieldA1;
}
class B1 extends Base1 {
    String fieldB1;
}

@JsonSubTypes({
    @JsonSubTypes.Type(value = A2.class),
    @JsonSubTypes.Type(value = B2.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
class Base2 {
    String sup;
}

class A2 extends Base2 {
    String fieldA2;
}
class B2 extends Base2 {
    String fieldB2;
}
''')
        def deserializer = (Deserializer) compiled.loadClass('example.$Base1$Deserializer').newInstance()
        def parsed = deserializeFromString(deserializer, '{"fieldA1":"foo","sup":"x","fieldA2":"bar"}')

        def serializer = (Serializer) compiled.loadClass('example.$Base1$Serializer').newInstance()
        def a1 = compiled.loadClass('example.A1').newInstance()
        a1.fieldA1 = 'foo'
        def a2 = compiled.loadClass('example.A2').newInstance()
        a2.sup = 'x'
        a2.fieldA2 = 'bar'
        a1.base2 = a2

        expect:
        parsed.fieldA1 == 'foo'
        parsed.base2.sup == 'x'
        parsed.base2.fieldA2 == 'bar'

        serializeToString(serializer, a1) == '{"fieldA1":"foo","fieldA2":"bar","sup":"x"}'
    }
}
