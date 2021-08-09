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
''')

        then:
        context.findBean(Argument.of(Serializer.class, context.classLoader.loadClass("example.Bean"))).isPresent()
    }
}
