package io.micronaut.json.generator

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.Serializer
import spock.lang.Specification

class GroovySupportSpec extends AbstractBeanDefinitionSpec {
    def test() {
        when:
        def cl = buildClassLoader('''
package example

import io.micronaut.json.annotation.SerializableBean

@SerializableBean
class Bean {
    String foo
}
''')
        def serializer = cl.loadClass('example.$Bean$Serializer')

        then:
        Serializer.class.isAssignableFrom(serializer)
    }

    def context() {
        when:
        def context = buildContext('''
package example

import io.micronaut.json.annotation.SerializableBean

@SerializableBean
class Bean {
    String foo
}
''', true)

        then:
        context.getBeansOfType(Serializer.Factory).any { it.genericType == context.classLoader.loadClass("example.Bean") }
    }

    def nested() {
        when:
        def cl = buildClassLoader('''
package example

import io.micronaut.json.annotation.SerializableBean

class Test {
    @SerializableBean
    static class Bean {
        String foo
    }
}
''')
        def serializer = cl.loadClass('example.$Test$Bean$Serializer')

        then:
        Serializer.class.isAssignableFrom(serializer)
    }

    def "json creator"() {
        when:
        def context = buildContext('''
package example

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.json.annotation.SerializableBean

@SerializableBean
class Bean {
    String foo
    
    @JsonCreator
    Bean(@JsonProperty("foo") String foo) {
        this.foo = foo
    }
}
''', true)

        then:
        context.getBeansOfType(Serializer.Factory).any { it.genericType == context.classLoader.loadClass("example.Bean") }
    }
}
