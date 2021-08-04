package io.micronaut.json.tree;

import com.fasterxml.jackson.core.JsonParser;

abstract class JsonContainer extends JsonNode {
    @Override
    public JsonParser.NumberType numberType() {
        return null;
    }

    @Override
    public boolean isContainerNode() {
        return true;
    }
}
