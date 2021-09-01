package io.micronaut.jackson.core.tree;

import com.fasterxml.jackson.core.*;
import io.micronaut.json.JsonConfig;
import io.micronaut.json.tree.JsonNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

public final class MicronautTreeCodec {
    private static final MicronautTreeCodec INSTANCE = new MicronautTreeCodec(JsonConfig.DEFAULT);

    private final JsonConfig config;

    private MicronautTreeCodec(JsonConfig config) {
        this.config = config;
    }

    public static MicronautTreeCodec getInstance() {
        return INSTANCE;
    }

    public MicronautTreeCodec withConfig(JsonConfig config) {
        return new MicronautTreeCodec(config);
    }

    @SuppressWarnings("unchecked")
    public final JsonNode readTree(JsonParser p) throws IOException {
        return readTree0(p);
    }

    private JsonNode readTree0(JsonParser p) throws IOException {
        switch (p.hasCurrentToken() ? p.currentToken() : p.nextToken()) {
            case START_OBJECT: {
                Map<String, JsonNode> values = new LinkedHashMap<>();
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    String name = p.currentName();
                    p.nextToken();
                    values.put(name, readTree0(p));
                }
                return JsonNode.createObjectNode(values);
            }
            case START_ARRAY: {
                List<JsonNode> values = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    values.add(readTree0(p));
                }
                return JsonNode.createArrayNode(values);
            }
            case VALUE_STRING:
                return JsonNode.createStringNode(p.getText());
            case VALUE_NUMBER_INT:
                if (config.useBigIntegerForInts()) {
                    return JsonNode.createNumberNode(p.getBigIntegerValue());
                } else {
                    // technically, we could get an unsupported number value here.
                    return JsonNode.createNumberNodeImpl(p.getNumberValue());
                }
            case VALUE_NUMBER_FLOAT:
                if (config.useBigDecimalForFloats()) {
                    return JsonNode.createNumberNode(p.getDecimalValue());
                } else {
                    // technically, we could get an unsupported number value here.
                    return JsonNode.createNumberNodeImpl(p.getNumberValue());
                }
            case VALUE_TRUE:
                return JsonNode.createBooleanNode(true);
            case VALUE_FALSE:
                return JsonNode.createBooleanNode(false);
            case VALUE_NULL:
                return JsonNode.nullNode();
            default:
                throw new UnsupportedOperationException("Unsupported token: " + p.currentToken());
        }
    }

    public void writeTree(JsonGenerator generator, JsonNode tree) throws IOException, JsonProcessingException {
        if (tree.isObject()) {
            generator.writeStartObject();
            for (Map.Entry<String, JsonNode> entry : tree.entries()) {
                generator.writeFieldName(entry.getKey());
                writeTree(generator, tree);
            }
            generator.writeEndObject();
        } else if (tree.isArray()) {
            generator.writeStartArray();
            for (JsonNode value : tree.values()) {
                writeTree(generator, value);
            }
            generator.writeEndArray();
        } else if (tree.isBoolean()) {
            generator.writeBoolean(tree.getBooleanValue());
        } else if (tree.isNull()) {
            generator.writeNull();
        } else if (tree.isNumber()) {
            Number value = tree.getNumberValue();
            if (value instanceof BigDecimal) {
                generator.writeNumber((BigDecimal) value);
            } else if (value instanceof Double) {
                generator.writeNumber(value.doubleValue());
            } else if (value instanceof Float) {
                generator.writeNumber(value.floatValue());
            } else if (value instanceof Integer) {
                generator.writeNumber(value.intValue());
            } else if (value instanceof Byte || value instanceof Short) {
                generator.writeNumber(value.shortValue());
            } else if (value instanceof Long) {
                generator.writeNumber(value.longValue());
            } else if (value instanceof BigInteger) {
                generator.writeNumber((BigInteger) value);
            } else {
                throw new IllegalStateException("Unknown number type " + value.getClass().getName());
            }
        }
    }

    public JsonParser treeAsTokens(JsonNode node) {
        return new TraversingParser(node);
    }

    public TreeGenerator createTreeGenerator() {
        return new TreeGenerator();
    }
}
