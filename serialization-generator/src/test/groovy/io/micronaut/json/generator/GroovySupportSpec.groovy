package io.micronaut.json.generator

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
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
        def serializer = cl.loadClass('example.Bean$Serializer')

        then:
        Serializer.class.isAssignableFrom(serializer)
    }
}
