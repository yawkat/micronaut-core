package io.micronaut.ast.groovy.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.SourceType

class GroovySourceTypeSpec extends AbstractBeanDefinitionSpec {
    def simple() {
        given:
        def element = buildClassElement('''
package test

class Test {}
''')
        when:
        def mnType = element.getRawSourceType()

        then:
        mnType instanceof SourceType.RawClass
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
        def mnType = element.withArrayDimensions(2).getRawSourceType()

        then:
        mnType instanceof SourceType.Array
        mnType.typeName == 'test.Test[][]'
    }

    def "type var definition"() {
        given:
        def element = buildClassElement('''
package test

class Test<A, B extends CharSequence, C extends Comparable<C>, D extends Number & CharSequence> {}
''')
        when:
        def mnType = element.getRawSourceType()

        then:
        mnType instanceof SourceType.RawClass
        mnType.typeName == 'test.Test'

        when:
        def args = ((SourceType.RawClass) mnType).typeVariables

        then:
        args.size() == 4

        args[0].name == 'A'
        args[0].bounds.size() == 1
        args[0].bounds[0] instanceof SourceType.RawClass
        args[0].bounds[0].typeName == 'java.lang.Object'
        args[0].declaringElement instanceof ClassElement
        args[0].declaringElement.name == 'test.Test'

        args[1].name == 'B'
        args[1].bounds.size() == 1
        args[1].bounds[0] instanceof SourceType.RawClass
        args[1].bounds[0].typeName == 'java.lang.CharSequence'
        args[1].declaringElement instanceof ClassElement
        args[1].declaringElement.name == 'test.Test'

        args[2].name == 'C'
        args[2].bounds.size() == 1
        args[2].bounds[0] instanceof SourceType.Parameterized
        args[2].bounds[0].outer == null
        args[2].bounds[0].raw.typeName == 'java.lang.Comparable'
        args[2].bounds[0].parameters.size() == 1
        //args[2].bounds[0].parameters[0] == args[2] unsupported
        args[2].declaringElement instanceof ClassElement
        args[2].declaringElement.name == 'test.Test'

        args[3].name == 'D'
        args[3].bounds.size() == 2
        args[3].bounds[0] instanceof SourceType.RawClass
        args[3].bounds[0].typeName == 'java.lang.Number'
        args[3].bounds[1] instanceof SourceType.RawClass
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
        def mnType = element.getFields()[0].declaredSourceType

        then:
        mnType instanceof SourceType.Parameterized
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
        def mnType = element.getEnclosedElement(ElementQuery.ALL_METHODS).get().declaredReturnSourceType

        then:
        mnType instanceof SourceType.Parameterized
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
        def mnType = element.getEnclosedElement(ElementQuery.ALL_METHODS).get().parameters[0].declaredSourceType

        then:
        mnType instanceof SourceType.Parameterized
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
        def mnType1 = element.fields[0].declaredSourceType
        def mnType2 = element.fields[1].declaredSourceType

        then:
        mnType1 instanceof SourceType.Variable
        //mnType1.declaringElement instanceof ClassElement unsupported
        //mnType1.declaringElement.name == 'test.Test'
        mnType1.name == 'E'

        mnType2 instanceof SourceType.Array
        mnType2.component instanceof SourceType.Variable
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
        def mnTypeA = element.fields[0].declaredSourceType
        def mnTypeB = element.fields[1].declaredSourceType

        then:
        mnTypeA instanceof SourceType.Parameterized
        mnTypeA.parameters[0] instanceof SourceType.Wildcard
        mnTypeA.parameters[0].lowerBounds.size() == 0
        mnTypeA.parameters[0].upperBounds.size() == 1
        mnTypeA.parameters[0].upperBounds[0].typeName == 'java.lang.Number'

        mnTypeB instanceof SourceType.Parameterized
        mnTypeB.parameters[0] instanceof SourceType.Wildcard
        mnTypeB.parameters[0].upperBounds.size() == 1
        mnTypeB.parameters[0].upperBounds[0].typeName == 'java.lang.Object'
        mnTypeB.parameters[0].lowerBounds.size() == 1
        mnTypeB.parameters[0].lowerBounds[0].typeName == 'java.lang.String'
    }

    def "supertype generic"() {
        given:
        def element = buildClassElement('''
package test

class Test extends ArrayList<String> {
}
''')
        when:
        def mnType = element.rawSourceType

        then:
        mnType instanceof SourceType.RawClass
        mnType.supertype instanceof SourceType.Parameterized
        mnType.supertype.raw.typeName == 'java.util.ArrayList'
        mnType.supertype.parameters[0].typeName == 'java.lang.String'
    }

    def "superinterface generic"() {
        given:
        def element = buildClassElement('''
package test

abstract class Test implements List<String> {
}
''')
        when:
        def mnType = element.rawSourceType

        then:
        mnType instanceof SourceType.RawClass
        mnType.interfaces[0] instanceof SourceType.Parameterized
        mnType.interfaces[0].raw.typeName == 'java.util.List'
        mnType.interfaces[0].parameters[0].typeName == 'java.lang.String'
    }

    def "supertype type var"() {
        given:
        def element = buildClassElement('''
package test

class Test<E> extends ArrayList<E> {
}
''')
        when:
        def mnType = element.rawSourceType

        then:
        mnType instanceof SourceType.RawClass
        mnType.supertype instanceof SourceType.Parameterized
        mnType.supertype.raw.typeName == 'java.util.ArrayList'
        mnType.supertype.parameters[0] instanceof SourceType.Variable
        mnType.supertype.parameters[0].name == 'E'
    }
}
