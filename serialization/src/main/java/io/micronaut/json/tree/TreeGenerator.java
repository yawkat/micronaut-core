/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.json.tree;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.jr.stree.*;
import io.micronaut.core.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

public final class TreeGenerator extends JsonGenerator {
    private ObjectCodec codec;
    private int generatorFeatures;

    private final Deque<StructureBuilder> structureStack = new ArrayDeque<>();
    private JrsValue completed = null;

    @Override
    public JsonGenerator setCodec(ObjectCodec oc) {
        this.codec = oc;
        return this;
    }

    @Override
    public ObjectCodec getCodec() {
        return codec;
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    @Override
    public JsonStreamContext getOutputContext() {
        return null;
    }

    @Override
    public JsonGenerator enable(Feature f) {
        generatorFeatures |= f.getMask();
        return this;
    }

    @Override
    public JsonGenerator disable(Feature f) {
        generatorFeatures &= ~f.getMask();
        return this;
    }

    @Override
    public boolean isEnabled(Feature f) {
        return (generatorFeatures & f.getMask()) != 0;
    }

    @Override
    public int getFeatureMask() {
        return generatorFeatures;
    }

    @Override
    public JsonGenerator setFeatureMask(int values) {
        generatorFeatures = values;
        return this;
    }

    @Override
    public JsonGenerator useDefaultPrettyPrinter() {
        return this;
    }

    private void checkEmptyNodeStack(JsonToken token) throws JsonGenerationException {
        if (structureStack.isEmpty()) {
            throw new JsonGenerationException("Unexpected " + tokenType(token) + " literal", this);
        }
    }

    private static String tokenType(JsonToken token) {
        switch (token) {
            case END_OBJECT:
            case END_ARRAY:
                return "container end";
            case FIELD_NAME:
                return "field";
            case VALUE_NUMBER_INT:
                return "integer";
            case VALUE_STRING:
                return "string";
            case VALUE_NUMBER_FLOAT:
                return "float";
            case VALUE_NULL:
                return "null";
            case VALUE_TRUE:
            case VALUE_FALSE:
                return "boolean";
            default:
                return "";
        }
    }

    private void complete(JrsValue value) {
        // todo
        completed = value;
    }

    public boolean isComplete() {
        return completed != null;
    }

    @NonNull
    public JrsValue getCompletedValue() {
        if (!isComplete()) {
            throw new IllegalStateException("Not completed");
        }
        return completed;
    }

    @Override
    public void writeStartArray() throws IOException {
        structureStack.push(new ArrayBuilder());
    }

    private void writeEndStructure(JsonToken token) throws JsonGenerationException {
        checkEmptyNodeStack(token);
        final StructureBuilder current = structureStack.pop();
        if (structureStack.isEmpty()) {
            complete(current.build());
        } else {
            structureStack.peekFirst().addValue(current.build());
        }
    }

    @Override
    public void writeEndArray() throws IOException {
        writeEndStructure(JsonToken.END_ARRAY);
    }

    @Override
    public void writeStartObject() throws IOException {
        structureStack.push(new ObjectBuilder());
    }

    @Override
    public void writeEndObject() throws IOException {
        writeEndStructure(JsonToken.END_OBJECT);
    }

    @Override
    public void writeFieldName(String name) throws IOException {
        checkEmptyNodeStack(JsonToken.FIELD_NAME);
        structureStack.peekFirst().setCurrentFieldName(name);
    }

    @Override
    public void writeFieldName(SerializableString name) throws IOException {
        writeFieldName(name.getValue());
    }

    private void writeScalar(JsonToken token, JrsValue value) throws JsonGenerationException {
        if (structureStack.isEmpty()) {
            complete(value);
        } else {
            structureStack.peekFirst().addValue(value);
        }
    }

    @Override
    public void writeString(String text) throws IOException {
        writeScalar(JsonToken.VALUE_STRING, new JrsString(text));
    }

    @Override
    public void writeString(char[] buffer, int offset, int len) throws IOException {
        writeString(new String(buffer, offset, len));
    }

    @Override
    public void writeString(SerializableString text) throws IOException {
        writeString(text.getValue());
    }

    @Override
    public void writeRawUTF8String(byte[] buffer, int offset, int len) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeUTF8String(byte[] buffer, int offset, int len) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(String text) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(String text, int offset, int len) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(char[] text, int offset, int len) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRaw(char c) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeRawValue(String text) throws IOException {
        writeObject(text);
    }

    @Override
    public void writeRawValue(String text, int offset, int len) throws IOException {
        writeRawValue(text.substring(offset, len));
    }

    @Override
    public void writeRawValue(char[] text, int offset, int len) throws IOException {
        writeRawValue(new String(text, offset, len));
    }

    @Override
    public void writeBinary(Base64Variant bv, byte[] data, int offset, int len) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public int writeBinary(Base64Variant bv, InputStream data, int dataLength) throws IOException {
        _reportUnsupportedOperation();
        return 0;
    }

    @Override
    public void writeNumber(int v) throws IOException {
        writeScalar(JsonToken.VALUE_NUMBER_INT, new JrsNumber(v));
    }

    @Override
    public void writeNumber(long v) throws IOException {
        writeScalar(JsonToken.VALUE_NUMBER_INT, new JrsNumber(v));
    }

    @Override
    public void writeNumber(BigInteger v) throws IOException {
        // todo: normalize
        writeScalar(JsonToken.VALUE_NUMBER_INT, new JrsNumber(v));
    }

    @Override
    public void writeNumber(double v) throws IOException {
        writeScalar(JsonToken.VALUE_NUMBER_FLOAT, new JrsNumber(v));
    }

    @Override
    public void writeNumber(float v) throws IOException {
        writeScalar(JsonToken.VALUE_NUMBER_FLOAT, new JrsNumber(v));
    }

    @Override
    public void writeNumber(BigDecimal v) throws IOException {
        writeScalar(JsonToken.VALUE_NUMBER_FLOAT, new JrsNumber(v));
    }

    @Override
    public void writeNumber(String encodedValue) throws IOException {
        _reportUnsupportedOperation();
    }

    @Override
    public void writeBoolean(boolean state) throws IOException {
        if (state) {
            writeScalar(JsonToken.VALUE_TRUE, JrsBoolean.TRUE);
        } else {
            writeScalar(JsonToken.VALUE_FALSE, JrsBoolean.FALSE);
        }
    }

    @Override
    public void writeNull() throws IOException {
        writeScalar(JsonToken.VALUE_NULL, JrsNull.instance());
    }

    @Override
    public void writeObject(Object pojo) throws IOException {
        writeScalar(JsonToken.VALUE_EMBEDDED_OBJECT, new JrsEmbeddedObject(pojo));
    }

    @Override
    public void writeTree(TreeNode rootNode) throws IOException {
        if (rootNode == null) {
            writeNull();
        } else if (rootNode instanceof JrsValue) {
            writeScalar(JsonToken.VALUE_EMBEDDED_OBJECT, (JrsValue) rootNode);
        } else {
            // todo: copy
        }
    }

    @Override
    public void flush() throws IOException {
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public void close() throws IOException {
    }

    private interface StructureBuilder {
        void addValue(JrsValue value) throws JsonGenerationException;

        void setCurrentFieldName(String currentFieldName) throws JsonGenerationException;

        JrsValue build();
    }

    private class ArrayBuilder implements StructureBuilder {
        final List<JrsValue> values = new ArrayList<>();

        @Override
        public void addValue(JrsValue value) {
            values.add(value);
        }

        @Override
        public void setCurrentFieldName(String currentFieldName) throws JsonGenerationException {
            throw new JsonGenerationException("Expected array value, got field name", TreeGenerator.this);
        }

        @Override
        public JrsValue build() {
            return new JrsArray(values);
        }
    }

    private class ObjectBuilder implements StructureBuilder {
        final Map<String, JrsValue> values = new HashMap<>();
        String currentFieldName = null;

        @Override
        public void addValue(JrsValue value) throws JsonGenerationException {
            if (currentFieldName == null) {
                throw new JsonGenerationException("Expected field name, got value", TreeGenerator.this);
            }
            values.put(currentFieldName, value);
            currentFieldName = null;
        }

        @Override
        public void setCurrentFieldName(String currentFieldName) {
            this.currentFieldName = currentFieldName;
        }

        @Override
        public JrsValue build() {
            return new JrsObject(values);
        }
    }
}
