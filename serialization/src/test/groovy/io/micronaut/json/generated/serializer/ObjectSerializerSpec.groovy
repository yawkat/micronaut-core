package io.micronaut.json.generated.serializer

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.Serializer
import io.micronaut.json.generated.GeneratedObjectCodec
import spock.lang.Specification

class ObjectSerializerSpec extends Specification {
    def test() {
        given:
        def ctx = ApplicationContext.run()
        def codec = ctx.getBean(GeneratedObjectCodec)

        expect:
        codec.readValue(codec.objectCodec.factory.createParser('{"foo":"bar"}'), Object) == ["foo": "bar"]
        codec.readValue(codec.objectCodec.factory.createParser('["foo"]'), Object) == ["foo"]
        codec.readValue(codec.objectCodec.factory.createParser('42'), Object) == 42
    }
}
