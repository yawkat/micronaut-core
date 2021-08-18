package io.micronaut.ast.groovy.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MnType

class GroovyMnTypeSpec extends AbstractBeanDefinitionSpec {
    def simple() {
        given:
        def element = buildClassElement('''
package test

class Test {}
''')
        when:
        def mnType = element.getRawMnType()

        then:
        mnType instanceof MnType.RawClass
        mnType.typeName == 'test.Test'
        mnType.typeVariables.isEmpty()
    }

    def array() {
        given:
        def element = buildClassElement('''
package test

class Test {}
''')
        when:
        def mnType = element.withArrayDimensions(2).getRawMnType()

        then:
        mnType instanceof MnType.Array
        mnType.typeName == 'test.Test[][]'
    }

    def "type var definition"() {
        given:
        def element = buildClassElement('''
package test

class Test<A, B extends CharSequence, C extends Comparable<C>, D extends Number & CharSequence> {}
''')
        when:
        def mnType = element.getRawMnType()

        then:
        mnType instanceof MnType.RawClass
        mnType.typeName == 'test.Test'

        when:
        def args = ((MnType.RawClass) mnType).typeVariables

        then:
        args.size() == 4

        args[0].name == 'A'
        args[0].bounds.size() == 1
        args[0].bounds[0] instanceof MnType.RawClass
        args[0].bounds[0].typeName == 'java.lang.Object'
        args[0].declaringElement instanceof ClassElement
        args[0].declaringElement.name == 'test.Test'

        args[1].name == 'B'
        args[1].bounds.size() == 1
        args[1].bounds[0] instanceof MnType.RawClass
        args[1].bounds[0].typeName == 'java.lang.CharSequence'
        args[1].declaringElement instanceof ClassElement
        args[1].declaringElement.name == 'test.Test'

        args[2].name == 'C'
        args[2].bounds.size() == 1
        args[2].bounds[0] instanceof MnType.Parameterized
        args[2].bounds[0].outer == null
        args[2].bounds[0].raw.typeName == 'java.lang.Comparable'
        args[2].bounds[0].parameters.size() == 1
        //args[2].bounds[0].parameters[0] == args[2] unsupported
        args[2].declaringElement instanceof ClassElement
        args[2].declaringElement.name == 'test.Test'

        args[3].name == 'D'
        args[3].bounds.size() == 2
        args[3].bounds[0] instanceof MnType.RawClass
        args[3].bounds[0].typeName == 'java.lang.Number'
        args[3].bounds[1] instanceof MnType.RawClass
        args[3].bounds[1].typeName == 'java.lang.CharSequence'
        args[3].declaringElement instanceof ClassElement
        args[3].declaringElement.name == 'test.Test'
    }

    def field() {
        given:
        def element = buildClassElement('''
package test

class Test {
    java.util.List<String> foo;
}
''')
        when:
        def mnType = element.getFields()[0].mnType

        then:
        mnType instanceof MnType.Parameterized
        mnType.typeName == 'java.util.List<java.lang.String>'
    }

    def method() {
        given:
        def element = buildClassElement('''
package test

abstract class Test {
    abstract java.util.List<String> foo();
}
''')
        when:
        def mnType = element.getEnclosedElement(ElementQuery.ALL_METHODS).get().mnReturnType

        then:
        mnType instanceof MnType.Parameterized
        mnType.typeName == 'java.util.List<java.lang.String>'
    }

    def parameter() {
        given:
        def element = buildClassElement('''
package test

class Test {
    void foo(java.util.List<String> list) {}
}
''')
        when:
        def mnType = element.getEnclosedElement(ElementQuery.ALL_METHODS).get().parameters[0].mnType

        then:
        mnType instanceof MnType.Parameterized
        mnType.typeName == 'java.util.List<java.lang.String>'
    }

    def "type param use"() {
        given:
        def element = buildClassElement('''
package test

class Test<E> {
    E field1
    E[] field2
}
''')
        when:
        def mnType1 = element.fields[0].mnType
        def mnType2 = element.fields[1].mnType

        then:
        mnType1 instanceof MnType.Variable
        //mnType1.declaringElement instanceof ClassElement unsupported
        //mnType1.declaringElement.name == 'test.Test'
        mnType1.name == 'E'

        mnType2 instanceof MnType.Array
        mnType2.component instanceof MnType.Variable
        mnType2.component.name == 'E'
    }

    def "wildcard"() {
        given:
        def element = buildClassElement('''
package test

class Test {
    java.util.List<? extends Number> a
    java.util.List<? super String> b
}
''')
        when:
        def mnTypeA = element.fields[0].mnType
        def mnTypeB = element.fields[1].mnType

        then:
        mnTypeA instanceof MnType.Parameterized
        mnTypeA.parameters[0] instanceof MnType.Wildcard
        mnTypeA.parameters[0].lowerBounds.size() == 0
        mnTypeA.parameters[0].upperBounds.size() == 1
        mnTypeA.parameters[0].upperBounds[0].typeName == 'java.lang.Number'

        mnTypeB instanceof MnType.Parameterized
        mnTypeB.parameters[0] instanceof MnType.Wildcard
        mnTypeB.parameters[0].upperBounds.size() == 1
        mnTypeB.parameters[0].upperBounds[0].typeName == 'java.lang.Object'
        mnTypeB.parameters[0].lowerBounds.size() == 1
        mnTypeB.parameters[0].lowerBounds[0].typeName == 'java.lang.String'
    }
}
