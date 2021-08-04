package io.micronaut.json.tree;

import com.fasterxml.jackson.core.*;
import io.micronaut.core.annotation.NonNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

public final class MicronautTreeCodec extends TreeCodec {
    private static final MicronautTreeCodec INSTANCE = new MicronautTreeCodec();

    private MicronautTreeCodec() {
    }

    public static MicronautTreeCodec getInstance() {
        return INSTANCE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T extends TreeNode> T readTree(JsonParser p) throws IOException {
        return (T) readTree0(p);
    }

    private JsonNode readTree0(JsonParser p) throws IOException {
        switch (p.hasCurrentToken() ? p.currentToken() : p.nextToken()) {
            case NOT_AVAILABLE:
                return createMissingNode();
            case START_OBJECT: {
                Map<String, JsonNode> values = new LinkedHashMap<>();
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    String name = p.currentName();
                    p.nextToken();
                    values.put(name, readTree0(p));
                }
                return createObjectNode(values);
            }
            case START_ARRAY: {
                List<JsonNode> values = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    values.add(readTree0(p));
                }
                return createArrayNode(values);
            }
            case VALUE_STRING:
                return createStringNode(p.getText());
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                // technically, we could get an unsupported number value here.
                return createNumberNode(p.getNumberValue());
            case VALUE_TRUE:
                return createBooleanNode(true);
            case VALUE_FALSE:
                return createBooleanNode(false);
            case VALUE_NULL:
                return createNullNode();
            default:
                throw new UnsupportedOperationException("Unsupported token: " + p.currentToken());
        }
    }

    @Override
    public void writeTree(JsonGenerator g, TreeNode tree) throws IOException, JsonProcessingException {
        ((JsonNode) tree).emit(g);
    }

    @Override
    public JsonNode createArrayNode() {
        return createArrayNode(Collections.emptyList());
    }

    public JsonNode createArrayNode(List<JsonNode> nodes) {
        return new JsonArray(nodes);
    }

    @Override
    public JsonNode createObjectNode() {
        return createObjectNode(Collections.emptyMap());
    }

    public JsonNode createObjectNode(Map<String, JsonNode> nodes) {
        return new JsonObject(nodes);
    }

    public JsonNode createMissingNode() {
        return JsonMissing.INSTANCE;
    }

    public JsonNode createNullNode() {
        return JsonNull.INSTANCE;
    }

    public JsonNode createNumberNode(int value) {
        return createNumberNode((Number) value);
    }

    public JsonNode createNumberNode(long value) {
        return createNumberNode((Number) value);
    }

    public JsonNode createNumberNode(float value) {
        return createNumberNode((Number) value);
    }

    public JsonNode createNumberNode(double value) {
        return createNumberNode((Number) value);
    }

    public JsonNode createNumberNode(@NonNull BigInteger value) {
        return createNumberNode((Number) value);
    }

    public JsonNode createNumberNode(@NonNull BigDecimal value) {
        return createNumberNode((Number) value);
    }

    /**
     * Hidden, so that we don't have to check that the number type is supported
     */
    private JsonNode createNumberNode(Number value) {
        Objects.requireNonNull(value, "value");
        return new JsonNumber(value);
    }

    public JsonNode createStringNode(@NonNull String value) {
        Objects.requireNonNull(value, "value");
        return new JsonString(value);
    }

    public JsonNode createBooleanNode(boolean value) {
        return JsonBoolean.valueOf(value);
    }

    @Override
    public JsonParser treeAsTokens(TreeNode node) {
        return node.traverse();
    }
}
