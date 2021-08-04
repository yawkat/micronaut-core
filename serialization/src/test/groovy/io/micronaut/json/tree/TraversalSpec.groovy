package io.micronaut.json.tree

import com.fasterxml.jackson.core.JsonFactory
import io.micronaut.json.GenericDeserializationConfig
import spock.lang.Specification

class TraversalSpec extends Specification {
    def roundtrip() {
        given:
        def factory = new JsonFactory()
        def codec = MicronautTreeCodec.getInstance()

        when:
        def stringWriter = new StringWriter()
        try (def generator = factory.createGenerator(stringWriter)) {
            JsonStreamTransfer.transferNext(node.traverse(), generator, GenericDeserializationConfig.DEFAULT)
        }
        def json = stringWriter.toString()

        def parsed = codec.readTree(factory.createParser(json))

        then:
        parsed == node

        where:
        node << [
                new JsonNumber(42),
                new JsonNumber(12345678901L),
                // new JsonNumber(42.5F), just casts to double
                new JsonNumber(12345678901.5D),
                new JsonNumber(new BigInteger("123456789012345678901234567890")),
                // new JsonNumber(new BigDecimal("123456789012345678901234567890.5")), just casts to double
                JsonNull.INSTANCE,
                // JsonMissing.INSTANCE, cannot be output
                JsonBoolean.valueOf(true),
                JsonBoolean.valueOf(false),
                new JsonString("foo"),

                new JsonObject(['a': new JsonNumber(1), 'b': new JsonNumber(2)]),
                new JsonArray([new JsonNumber(1), new JsonNumber(2)]),
                new JsonObject(['a': new JsonNumber(1), 'b': new JsonArray([new JsonNumber(3), new JsonNumber(4)])]),
        ]
    }
}
