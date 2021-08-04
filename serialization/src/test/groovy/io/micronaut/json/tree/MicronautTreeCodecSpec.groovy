package io.micronaut.json.tree

import com.fasterxml.jackson.core.JsonFactory
import spock.lang.Specification

class MicronautTreeCodecSpec extends Specification {
    def readTree() {
        given:
        def c = MicronautTreeCodec.getInstance()

        expect:
        c.readTree(new JsonFactory().createParser('"bar"')) == c.createStringNode("bar")
        c.readTree(new JsonFactory().createParser('42')) == c.createNumberNode(42)
        c.readTree(new JsonFactory().createParser('true')) == c.createBooleanNode(true)
        c.readTree(new JsonFactory().createParser('null')) == c.nullNode()
        c.readTree(new JsonFactory().createParser('{"foo":"bar","x":42}')) ==
                c.createObjectNode(["foo": c.createStringNode("bar"), "x": c.createNumberNode(42)])
        c.readTree(new JsonFactory().createParser('["bar",42]')) ==
                c.createArrayNode([c.createStringNode("bar"), c.createNumberNode(42)])
    }
}
