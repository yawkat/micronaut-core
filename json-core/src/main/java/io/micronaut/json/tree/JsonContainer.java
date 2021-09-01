package io.micronaut.json.tree;

abstract class JsonContainer extends JsonNode {
    @Override
    public boolean isContainerNode() {
        return true;
    }
}
