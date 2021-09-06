package io.micronaut.json.generator

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.BeanProvider
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.json.Deserializer
import io.micronaut.json.Serializer
import io.micronaut.json.generator.symbol.SingletonSerializerGenerator
import jakarta.inject.Provider

import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

class MapperVisitorSpec extends AbstractTypeElementSpec implements SerializerUtils {
    void "generator creates a serializer for jackson annotations"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;@io.micronaut.json.annotation.SerializableBean
public class Test {
}
''')

        def serializerClass = compiled.loadClass('example.$Test$Serializer')

        expect:
        serializerClass != null
        Serializer.class.isAssignableFrom(serializerClass)
    }

    void "nested beans"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;@io.micronaut.json.annotation.SerializableBean
class A {
    B b;
    String bar;
}

@io.micronaut.json.annotation.SerializableBean
class B {
    String foo;
}
''')

        def a = compiled.loadClass("example.A").newInstance()
        def b = compiled.loadClass("example.B").newInstance()

        a.b = b
        a.bar = "123"
        b.foo = "456"

        def serializerB = (Serializer<?>) compiled.loadClass('example.$B$Serializer').newInstance()
        def serializerA = (Serializer<?>) compiled.loadClass('example.$A$Serializer').newInstance(serializerB)

        def deserializerB = (Deserializer<?>) compiled.loadClass('example.$B$Deserializer').newInstance()
        def deserializerA = (Deserializer<?>) compiled.loadClass('example.$A$Deserializer').newInstance(deserializerB)

        expect:
        serializeToString(serializerB, b) == '{"foo":"456"}'
        serializeToString(serializerA, a) == '{"b":{"foo":"456"},"bar":"123"}'
        deserializeFromString(deserializerB, '{"foo":"456"}').foo == "456"
        deserializeFromString(deserializerA, '{"b":{"foo":"456"},"bar":"123"}').bar == "123"
        deserializeFromString(deserializerA, '{"b":{"foo":"456"},"bar":"123"}').b.foo == "456"
    }

    void "lists"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;import java.util.List;

@io.micronaut.json.annotation.SerializableBean
class Test {
    List<String> list;
}
''')

        def test = compiled.loadClass("example.Test").newInstance()

        test.list = ['foo', 'bar']

        def serializer = (Serializer<?>) compiled.loadClass('example.$Test$Serializer').newInstance()
        def deserializer = (Deserializer<?>) compiled.loadClass('example.$Test$Deserializer').newInstance()

        expect:
        serializeToString(serializer, test) == '{"list":["foo","bar"]}'
        deserializeFromString(deserializer, '{"list":["foo","bar"]}').list == ['foo', 'bar']
    }

    void "maps"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;
import java.util.Map;

@io.micronaut.json.annotation.SerializableBean
class Test {
    Map<String, String> map;
}
''')

        def test = compiled.loadClass("example.Test").newInstance()
        test.map = ['foo': 'bar']

        def serializer = (Serializer<?>) compiled.loadClass('example.$Test$Serializer').newInstance()
        def deserializer = (Deserializer<?>) compiled.loadClass('example.$Test$Deserializer').newInstance()

        expect:
        serializeToString(serializer, test) == '{"map":{"foo":"bar"}}'
        deserializeFromString(deserializer, '{"map":{"foo":"bar"}}').map == ['foo': 'bar']
    }

    void "null map values"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;
import java.util.Map;

@io.micronaut.json.annotation.SerializableBean
class Test {
    Map<String, String> map;
}
''')

        def test = compiled.loadClass("example.Test").newInstance()
        test.map = ['foo': null]

        def serializer = (Serializer<?>) compiled.loadClass('example.$Test$Serializer').newInstance()
        def deserializer = (Deserializer<?>) compiled.loadClass('example.$Test$Deserializer').newInstance()

        expect:
        serializeToString(serializer, test) == '{"map":{"foo":null}}'
        deserializeFromString(deserializer, '{"map":{"foo":null}}').map == ['foo': null]
    }

    void "recursive with proper annotation"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.RecursiveSerialization;
import io.micronaut.json.annotation.SerializableBean;

@io.micronaut.json.annotation.SerializableBean
class Test {
    @io.micronaut.json.annotation.RecursiveSerialization Test foo;
}
''')

        def test = compiled.loadClass("example.Test").newInstance()
        test.foo = compiled.loadClass("example.Test").newInstance()

        def providerSer = new Provider() {
            @Override
            Object get() {
                return compiled.loadClass('example.$Test$Serializer').newInstance(this)
            }
        }
        def serializer = providerSer.get()

        def providerDes = new Provider() {
            @Override
            Object get() {
                return compiled.loadClass('example.$Test$Deserializer').newInstance(this)
            }
        }
        def deserializer = providerDes.get()

        expect:
        // serializeToString(serializer, test) == '{"list":["foo","bar"]}' todo: null support
        deserializeFromString(deserializer, '{"foo":{}}').foo.foo == null
    }

    void "simple recursive without proper annotation gives error"() {
        when:
        buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;@io.micronaut.json.annotation.SerializableBean
class Test {
    Test foo;
}
''')
        then:
        def e = thrown Exception

        expect:
        e.message.contains("Circular dependency")
        e.message.contains("foo")
    }

    void "list recursive without proper annotation gives error"() {
        when:
        buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;@io.micronaut.json.annotation.SerializableBean
class Test {
    Test[] foo;
}
''')
        then:
        def e = thrown Exception

        expect:
        e.message.contains("Circular dependency")
        e.message.contains("foo")
    }

    void "mutually recursive without proper annotation gives error"() {
        when:
        buildClassLoader('example.A', '''
package example;

import io.micronaut.json.annotation.SerializableBean;@io.micronaut.json.annotation.SerializableBean
class A {
    B b;
}
@io.micronaut.json.annotation.SerializableBean
class B {
    A a;
}
''')
        then:
        def e = thrown Exception

        expect:
        e.message.contains("Circular dependency")
        e.message.contains("A->b->*->a->*")
    }

    void "recursive ref to type with dedicated serializer doesn't error"() {
        // todo: this is sensible behavior since the user may decide to supply her own Serializer<B>, but is it intuitive?
        when:
        buildClassLoader('example.A', '''
package example;

import io.micronaut.json.annotation.SerializableBean;@io.micronaut.json.annotation.SerializableBean
class A {
    B b;
}
// not annotated
class B {
    A a;
}
''')
        then:
        return
    }

    void "nested generic"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;@io.micronaut.json.annotation.SerializableBean
class A {
    B<C> b;
}

@io.micronaut.json.annotation.SerializableBean
class B<T> {
    T foo;
}

@io.micronaut.json.annotation.SerializableBean
class C {
    String bar;
}
''')

        def a = compiled.loadClass("example.A").newInstance()
        def b = compiled.loadClass("example.B").newInstance()
        def c = compiled.loadClass("example.C").newInstance()

        a.b = b
        b.foo = c
        c.bar = "123"

        def serializerC = (Serializer<?>) compiled.loadClass('example.$C$Serializer').newInstance()
        def serializerBClass = compiled.loadClass('example.$B_T_$Serializer')
        def serializerB = (Serializer<?>) serializerBClass.newInstance(serializerC)
        def serializerA = (Serializer<?>) compiled.loadClass('example.$A$Serializer').newInstance(serializerB)

        def genericSerializerParam = serializerBClass.getDeclaredConstructor(Serializer.class).getGenericParameterTypes()[0]

        def deserializerC = (Deserializer<?>) compiled.loadClass('example.$C$Deserializer').newInstance()
        def deserializerBClass = compiled.loadClass('example.$B_T_$Deserializer')
        def deserializerB = (Deserializer<?>) deserializerBClass.newInstance(deserializerC)
        def deserializerA = (Deserializer<?>) compiled.loadClass('example.$A$Deserializer').newInstance(deserializerB)

        def genericDeserializerParam = deserializerBClass.getDeclaredConstructor(Deserializer.class).getGenericParameterTypes()[0]

        expect:
        serializeToString(serializerA, a) == '{"b":{"foo":{"bar":"123"}}}'
        deserializeFromString(deserializerA, '{"b":{"foo":{"bar":"123"}}}').b.foo.bar == "123"

        genericSerializerParam instanceof ParameterizedType
        ((ParameterizedType) genericSerializerParam).actualTypeArguments[0] instanceof WildcardType
        ((ParameterizedType) genericSerializerParam).actualTypeArguments[0].lowerBounds[0] instanceof TypeVariable

        genericDeserializerParam instanceof ParameterizedType
        ((ParameterizedType) genericDeserializerParam).actualTypeArguments[0] instanceof WildcardType
        ((ParameterizedType) genericDeserializerParam).actualTypeArguments[0].upperBounds[0] instanceof TypeVariable
    }

    void "nested generic inline"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;@io.micronaut.json.annotation.SerializableBean
class A {
    B<C> b;
}

@io.micronaut.json.annotation.SerializableBean(inline = true)
class B<T> {
    T foo;
}

@io.micronaut.json.annotation.SerializableBean
class C {
    String bar;
}
''')

        def a = compiled.loadClass("example.A").newInstance()
        def b = compiled.loadClass("example.B").newInstance()
        def c = compiled.loadClass("example.C").newInstance()

        a.b = b
        b.foo = c
        c.bar = "123"

        def serializerC = (Serializer<?>) compiled.loadClass('example.$C$Serializer').newInstance()
        def serializerA = (Serializer<?>) compiled.loadClass('example.$A$Serializer').newInstance(serializerC)
        def deserializerC = (Deserializer<?>) compiled.loadClass('example.$C$Deserializer').newInstance()
        def deserializerA = (Deserializer<?>) compiled.loadClass('example.$A$Deserializer').newInstance(deserializerC)

        expect:
        serializeToString(serializerA, a) == '{"b":{"foo":{"bar":"123"}}}'
        deserializeFromString(deserializerA, '{"b":{"foo":{"bar":"123"}}}').b.foo.bar == "123"
    }

    void "enum"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import io.micronaut.json.annotation.SerializableBean;@io.micronaut.json.annotation.SerializableBean
class A {
    E e;
}

enum E {
    A, B
}
''')

        def a = compiled.loadClass("example.A").newInstance()
        a.e = compiled.loadClass("example.E").enumConstants[1]

        def serializerA = (Serializer<?>) compiled.loadClass('example.$A$Serializer').newInstance()
        def deserializerA = (Deserializer<?>) compiled.loadClass('example.$A$Deserializer').newInstance()

        expect:
        serializeToString(serializerA, a) == '{"e":"B"}'
        deserializeFromString(deserializerA, '{"e":"A"}').e.name() == 'A'
        deserializeFromString(deserializerA, '{"e":"B"}').e.name() == 'B'
    }

    void "nested class"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

class A {
    @io.micronaut.json.annotation.SerializableBean
    static class B {
    }
}
''')

        def b = compiled.loadClass('example.A$B').newInstance()
        def serializerB = (Serializer<?>) compiled.loadClass('example.$A$B$Serializer').newInstance()

        expect:
        serializeToString(serializerB, b) == '{}'
    }

    void "interface"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

@io.micronaut.json.annotation.SerializableBean(allowDeserialization = false)
interface Test {
    String getFoo();
}
''')
        def testBean = ['getFoo': { Object[] args -> 'bar' }].asType(compiled.loadClass('example.Test'))
        def serializer = compiled.loadClass('example.$Test$Serializer').newInstance()

        expect:
        serializeToString(serializer, testBean) == '{"foo":"bar"}'

        when:
        compiled.loadClass('example.$Test$Deserializer')

        then:
        thrown ClassNotFoundException
    }

    void "optional"() {
        given:
        def compiled = buildClassLoader('example.A', '''
package example;

@io.micronaut.json.annotation.SerializableBean
class A {
    java.util.Optional<B> b;
}

@io.micronaut.json.annotation.SerializableBean
class B {
}
''')
        def testBean = compiled.loadClass("example.A").newInstance()
        testBean.b = Optional.of(compiled.loadClass("example.B").newInstance())

        def serializerB = compiled.loadClass('example.$B$Serializer').newInstance()
        def serializer = compiled.loadClass('example.$A$Serializer').newInstance(serializerB)

        def deserializerB = compiled.loadClass('example.$B$Deserializer').newInstance()
        def deserializer = compiled.loadClass('example.$A$Deserializer').newInstance(deserializerB)

        expect:
        serializeToString(serializer, testBean) == '{"b":{}}'
        deserializeFromString(deserializer, '{"b":{}}').b.isPresent()
    }

    void "generic injection collision"() {
        given:
        def ctx = buildContext('example.A', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
class A {
}

@SerializableBean
class B extends A {
}

@SerializableBean
class C {
    A a;
}
''', true)

        expect:
        ctx.getBeansOfType(Serializer.Factory).any { it.genericType == ctx.classLoader.loadClass('example.C') }
    }

    void "disabled mode isn't generated"() {
        given:
        def ctx = buildContext('example.A', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean(allowDeserialization = false)
class A {
}
''', true)

        expect:
        ctx.getBeansOfType(Serializer.Factory).any { it.genericType == ctx.classLoader.loadClass('example.A') }
        !ctx.getBeansOfType(Deserializer.Factory).any { it.genericType == ctx.classLoader.loadClass('example.A') }
    }

    void "enum generated"() {
        given:
        def ctx = buildContext('example.Foo', '''
package example;

import io.micronaut.json.annotation.SerializableBean;

@SerializableBean
enum Foo {
    A, B
}
''', true)

        def serializer = ctx.getBeansOfType(Serializer.Factory)
                .find { it.genericType == ctx.classLoader.loadClass('example.Foo') }
                .newInstance(null, null)

        expect:
        serializeToString(serializer, Enum.valueOf(ctx.classLoader.loadClass('example.Foo'), 'A')) == '"A"'
    }

    void "injected serializer uses Serializer.isEmpty"() {
        given:
        def ctx = buildContext('example.A', '''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.json.Encoder;
import io.micronaut.json.annotation.SerializableBean;
import jakarta.inject.Singleton;

@SerializableBean(allowDeserialization = false)
class A {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    B b;
}

class B {
    boolean present;
}

@Singleton
class BSerializer implements io.micronaut.json.Serializer<B> {
    @Override
    public void serialize(Encoder encoder, B value) throws java.io.IOException {
        encoder.encodeBoolean(value.present);
    }

    @Override
    public boolean isEmpty(B value) {
        return !value.present;
    }
}
''', true)
        def bSerializer = ctx.getBean(ctx.classLoader.loadClass('example.BSerializer'))
        def aSerializer = ctx.classLoader.loadClass('example.$A$Serializer').newInstance(bSerializer)

        def bPresent = ctx.classLoader.loadClass('example.B').newInstance()
        bPresent.present = true
        def bAbsent = ctx.classLoader.loadClass('example.B').newInstance()
        bAbsent.present = false

        def aPresent = ctx.classLoader.loadClass('example.A').newInstance()
        aPresent.b = bPresent
        def aAbsent = ctx.classLoader.loadClass('example.A').newInstance()
        aAbsent.b = bAbsent

        expect:
        serializeToString(aSerializer, aPresent) == '{"b":true}'
        serializeToString(aSerializer, aAbsent) == '{}'
    }

    void "mixin visitor"() {
        given:
        def ctx = buildContext('example.A', '''
package example;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.json.annotation.SerializableBean;
import io.micronaut.json.annotation.SerializationMixin;
import jakarta.inject.Singleton;

@SerializationMixin(forClass = A.class)
class Marker {}

class A {
    String foo;
}
''', true)

        expect:
        ctx.getBeansOfType(Serializer.Factory).any { it.genericType == ctx.classLoader.loadClass('example.A') }
    }
}
