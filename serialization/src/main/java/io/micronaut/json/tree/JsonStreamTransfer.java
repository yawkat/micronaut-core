package io.micronaut.json.tree;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import io.micronaut.json.GenericDeserializationConfig;

import java.io.IOException;

public final class JsonStreamTransfer {
    private JsonStreamTransfer() {
    }

    public static void transferNext(JsonParser from, JsonGenerator to, GenericDeserializationConfig config) throws IOException {
        from.nextToken();
        transfer(from, to, config);
    }

    public static void transfer(JsonParser from, JsonGenerator to, GenericDeserializationConfig config) throws IOException {
        do {
            transferCurrentToken(from, to, config);
        } while (from.nextToken() != null);
    }

    public static void transferCurrentToken(
            JsonParser from,
            JsonGenerator to,
            GenericDeserializationConfig config
    ) throws IOException {
        switch (from.currentToken()) {
            case START_OBJECT:
                to.writeStartObject();
                break;
            case END_OBJECT:
                to.writeEndObject();
                break;
            case START_ARRAY:
                to.writeStartArray();
                break;
            case END_ARRAY:
                to.writeEndArray();
                break;
            case FIELD_NAME:
                to.writeFieldName(from.currentName());
                break;
            case VALUE_EMBEDDED_OBJECT:
                to.writeObject(from.getEmbeddedObject());
                break;
            case VALUE_STRING:
                to.writeString(from.getText());
                break;
            case VALUE_NUMBER_INT:
                if (config.useBigIntegerForInts()) {
                    to.writeNumber(from.getBigIntegerValue());
                } else {
                    final JsonParser.NumberType numberIntType = from.getNumberType();
                    switch (numberIntType) {
                        case BIG_INTEGER:
                            to.writeNumber(from.getBigIntegerValue());
                            break;
                        case LONG:
                            to.writeNumber(from.getLongValue());
                            break;
                        case INT:
                            to.writeNumber(from.getIntValue());
                            break;
                        default:
                            throw new IllegalStateException("Unsupported number type: " + numberIntType);
                    }
                }
                break;
            case VALUE_NUMBER_FLOAT:
                if (config.useBigDecimalForFloats()) {
                    to.writeNumber(from.getDecimalValue());
                } else {
                    final JsonParser.NumberType numberDecimalType = from.getNumberType();
                    switch (numberDecimalType) {
                        case FLOAT:
                            to.writeNumber(from.getFloatValue());
                            break;
                        case DOUBLE:
                            to.writeNumber(from.getDoubleValue());
                            break;
                        case BIG_DECIMAL:
                            to.writeNumber(from.getDecimalValue());
                            break;
                        default:
                            throw new IllegalStateException("Unsupported number type: " + numberDecimalType);
                    }
                }
                break;
            case VALUE_TRUE:
                to.writeBoolean(true);
                break;
            case VALUE_FALSE:
                to.writeBoolean(false);
                break;
            case VALUE_NULL:
                to.writeNull();
                break;
            default:
                throw new IllegalStateException("Unsupported JSON token: " + from.currentToken());
        }
    }
}
