package io.micronaut.json.generated

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.type.TypeReference
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.TestCls
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class GeneratedObjectCodecSpec extends Specification {
    def readValue() {
        when:
        def ctx = ApplicationContext.run()
        def codec = new GeneratedObjectCodec(ctx)
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
        def codec = new GeneratedObjectCodec(ctx)

        then:
        new String(codec.writeValueAsBytes(new TestCls("bar")), StandardCharsets.UTF_8) == '{"foo":"bar"}'
    }
}
