package io.micronaut.json.generated

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.type.TypeReference
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument

import io.micronaut.json.annotation.SerializableBean
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class GeneratedObjectCodecSpec extends Specification {
    def readValue() {
        when:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectCodec)
        def factory = new JsonFactory()

        then:
        codec.readValue(factory.createParser('{"foo":"bar"}'), TestCls.class).foo == 'bar'
        codec.readValue(factory.createParser('{"foo":"bar"}'), Argument.of(TestCls.class)).foo == 'bar'
        codec.objectCodec.readValue(factory.createParser('{"foo":"bar"}'), TestCls.class).foo == 'bar'
        codec.objectCodec.readValue(factory.createParser('{"foo":"bar"}'), new TypeReference<TestCls>() {}).foo == 'bar'
    }

    def writeValueAsBytes() {
        when:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectCodec)

        then:
        new String(codec.writeValueAsBytes(new TestCls("bar")), StandardCharsets.UTF_8) == '{"foo":"bar"}'
    }

    @SerializableBean
    static class TestCls {
        public final String foo

        @JsonCreator
        TestCls(@JsonProperty("foo") String foo) {
            this.foo = foo
        }
    }

    def "super type serializable"() {
        given:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectCodec)

        def value = new Subclass()
        value.foo = "42"
        value.bar = "24"

        when:
        def bytes = codec.writeValueAsBytes(value)

        then:"use the serializer for the base class"
        new String(bytes, StandardCharsets.UTF_8) == '{"foo":"42"}'

        //then:"to avoid confusion because of missing subclass properties, don't allow serialization"
        //thrown Exception
    }

    @SerializableBean
    static class Base {
        String foo
    }

    static class Subclass extends Base {
        String bar
    }

    def "generic bean deser"() {
        given:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectCodec)
        def factory = codec.objectCodec.factory

        when:
        def parsed = codec.readValue(factory.createParser('{"naked":"foo","list":["bar"]}'), Argument.of(GenericBean.class, String.class))

        then:
        parsed.naked == 'foo'
        parsed.list == ['bar']
    }

    def "generic bean ser"() {
        given:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectCodec)
        def factory = codec.objectCodec.factory

        def bean = new GenericBean<String>()
        bean.naked = "foo"
        bean.list = ["bar"]
        def wrapper = new GenericBeanWrapper()
        wrapper.bean = bean

        when:
        // there's currently no way to pass the type to write, and because of type erasure the framework can't know that
        // bean is a GenericBean<String>, so we use a wrapper (with @JsonValue) that has the full type.
        def json = new String(codec.writeValueAsBytes(wrapper), StandardCharsets.UTF_8)

        then:
        json == '{"naked":"foo","list":["bar"]}'
    }

    @SerializableBean
    static class GenericBean<T> {
        T naked
        List<T> list
    }

    @SerializableBean
    static class GenericBeanWrapper {
        @JsonValue GenericBean<String> bean;
    }

    def "raw map"() {
        given:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectCodec)
        def factory = codec.objectCodec.factory

        when:
        def parsed = codec.readValue(factory.createParser('{"string":"foo","list":["bar"]}'), Map.class)

        then:
        parsed == [string: 'foo', list: ['bar']]

        when:
        def json = new String(codec.writeValueAsBytes([string: 'foo', list: ['bar']]), StandardCharsets.UTF_8)

        then:
        json == '{"string":"foo","list":["bar"]}'
    }

    def "top-level null"() {
        given:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectCodec)
        def factory = codec.objectCodec.factory

        when:
        def parsedNaked = codec.readValue(factory.createParser('null'), String.class)
        then:
        parsedNaked == null

        when:
        def parsedOpt = codec.readValue(factory.createParser('null'), Argument.of(Optional.class, String.class))
        then:
        parsedOpt == Optional.empty()
    }
}
