package io.micronaut.core.beans

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class KotlinBeanIntrospectionSpec {

    @Test
    fun testWithValueOnKotlinDataClassWithDefaultValues() {
        val introspection = BeanIntrospection.getIntrospection(SomeEntity::class.java)

        val instance = introspection.instantiate(10L, "foo")

        assertEquals(10, instance.id)
        assertEquals("foo", instance.something)

        val changed = introspection.getRequiredProperty("something", String::class.java)
                .withValue(instance, "changed")

        assertEquals(10, changed.id)
        assertEquals("changed", changed.something)

    }
}