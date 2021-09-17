package io.micronaut.json.generated

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.annotation.JsonView
import com.fasterxml.jackson.core.JsonFactory
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.core.type.Argument
import io.micronaut.json.Encoder
import io.micronaut.json.Serializer
import io.micronaut.json.annotation.CustomSerializer
import io.micronaut.json.annotation.SerializableBean
import jakarta.inject.Singleton
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class GeneratedObjectCodecSpec extends Specification {
    def readValue() {
        when:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectMapper)
        def factory = new JsonFactory()

        then:
        codec.readValue('{"foo":"bar"}', Argument.of(TestCls.class)).foo == 'bar'
    }

    def writeValueAsBytes() {
        when:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectMapper)

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
        def codec = ctx.getBean(GeneratedObjectMapper)

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
        public String foo
    }

    static class Subclass extends Base {
        public String bar
    }

    def "generic bean deser"() {
        given:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectMapper)

        when:
        def parsed = codec.readValue('{"naked":"foo","list":["bar"]}', Argument.of(GenericBean.class, String.class))

        then:
        parsed.naked == 'foo'
        parsed.list == ['bar']
    }

    def "generic bean ser"() {
        given:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectMapper)

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
        public T naked
        public List<T> list
    }

    @SerializableBean
    static class GenericBeanWrapper {
        @JsonValue public GenericBean<String> bean;
    }

    def "raw map"() {
        given:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectMapper)

        when:
        def parsed = codec.readValue('{"string":"foo","list":["bar"]}', Argument.of(Map.class))

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
        def codec = ctx.getBean(GeneratedObjectMapper)

        when:
        def parsedNaked = codec.readValue('null', Argument.of(String.class))
        then:
        parsedNaked == null

        when:
        def parsedOpt = codec.readValue('null', Argument.of(Optional.class, String.class))
        then:
        parsedOpt == Optional.empty()
    }

    def "Map<Object, V>"() {
        given:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectMapper)

        when:
        def parsed = codec.readValue('{"foo":42}', Argument.mapOf(Object, Integer))
        then:
        parsed == [foo: 42]
    }

    def 'views'() {
        given:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectMapper)

        def withViews = new WithViews(firstName: "Bob", lastName: "Jones", birthdate: "08/01/1980", password: "secret")

        expect:
        new String(codec.cloneWithViewClass(Views.Admin).writeValueAsBytes(withViews), StandardCharsets.UTF_8) ==
                '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}'
        new String(codec.cloneWithViewClass(Views.Internal).writeValueAsBytes(withViews), StandardCharsets.UTF_8) ==
                '{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980"}'
        new String(codec.cloneWithViewClass(Views.Public).writeValueAsBytes(withViews), StandardCharsets.UTF_8) ==
                '{"firstName":"Bob","lastName":"Jones"}'
        new String(codec.writeValueAsBytes(withViews), StandardCharsets.UTF_8) == '{}'

        codec.readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .firstName == null

        codec.cloneWithViewClass(Views.Public).readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .firstName == 'Bob'
        codec.cloneWithViewClass(Views.Public).readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .birthdate == null

        codec.cloneWithViewClass(Views.Internal).readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .firstName == 'Bob'
        codec.cloneWithViewClass(Views.Internal).readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .birthdate == '08/01/1980'
        codec.cloneWithViewClass(Views.Internal).readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .password == null

        codec.cloneWithViewClass(Views.Admin).readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .firstName == 'Bob'
        codec.cloneWithViewClass(Views.Admin).readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .birthdate == '08/01/1980'
        codec.cloneWithViewClass(Views.Admin).readValue('{"firstName":"Bob","lastName":"Jones","birthdate":"08/01/1980","password":"secret"}', Argument.of(WithViews))
                .password == 'secret'
    }

    @SerializableBean
    @JsonView(Views.Public)
    static class WithViews {
        public String firstName
        public String lastName
        @JsonView(Views.Internal)
        public String birthdate
        @JsonView(Views.Admin)
        public String password // don't do plaintext passwords at home please
    }

    static class Views {
        static class Public {}

        static class Internal extends Public {}

        static class Admin extends Internal {}
    }

    def 'custom serializer does not collide with native serializers'() {
        // note: this test isnt very robust, because SerializerLocator may use the 'right' Serializer for String even
        // when UpperCaseSer would be eligible.

        given:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectMapper)

        def bean = new CustomSerializerBean(foo: 'boo', bar: 'Baz')
        expect:
        new String(codec.writeValueAsBytes(bean), StandardCharsets.UTF_8) == '{"foo":"boo","bar":"BAZ"}'
        new String(codec.writeValueAsBytes('Baz'), StandardCharsets.UTF_8) == '"Baz"'
    }

    @SerializableBean
    static class CustomSerializerBean {
        public String foo
        @CustomSerializer(serializer = UpperCaseSer.class)
        public String bar
    }

    @Singleton
    @Bean(typed = UpperCaseSer.class)
    static class UpperCaseSer implements Serializer<String> {
        @Override
        void serialize(Encoder encoder, String value) throws IOException {
            encoder.encodeString(value.toUpperCase(Locale.ROOT))
        }

        @Override
        boolean isEmpty(String value) {
            return value.isEmpty()
        }
    }
}
