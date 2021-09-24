package io.micronaut.json.generator.symbol

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement

class ClassElementUtilSpec extends AbstractTypeElementSpec {
    def 'findParameterization'() {
        given:
        def element = buildClassElement("""
package example;

import java.util.*;

class Sub<U> extends Sup<List<U>> {
}
class Sup<U> extends Supsup<List<U>> {
}
class Supsup<T extends Iterable<?>> {
}
""")
        def baseRawClass = element.superType.get().superType.get().rawClass

        expect:
        reconstruct(ClassElementUtil.findParameterization(element, baseRawClass).get()) == 'Supsup<List<List>>'
        reconstruct(ClassElementUtil.findParameterization(element.bindTypeArguments(element.declaredTypeVariables), baseRawClass).get()) == 'Supsup<List<List<U>>>'
        reconstruct(ClassElementUtil.findParameterization(element.bindTypeArguments([ClassElement.of(String)]), baseRawClass).get()) == 'Supsup<List<List<String>>>'
    }

    def 'findParameterization wildcard'() {
        given:
        def element = buildClassElement("""
package example;

import java.util.*;

class Sub<U> extends Sup<List<? super U>> {
}
class Sup<U> extends Supsup<List<? extends U>> {
}
class Supsup<T extends Iterable<?>> {
}
""")
        def baseRawClass = element.superType.get().superType.get().rawClass

        expect:
        reconstruct(ClassElementUtil.findParameterization(element, baseRawClass).get()) == 'Supsup<List<? extends List>>'
        reconstruct(ClassElementUtil.findParameterization(element.bindTypeArguments(element.declaredTypeVariables), baseRawClass).get()) == 'Supsup<List<? extends List<? super U>>>'
        reconstruct(ClassElementUtil.findParameterization(element.bindTypeArguments([ClassElement.of(String)]), baseRawClass).get()) == 'Supsup<List<? extends List<? super String>>>'
    }
}
