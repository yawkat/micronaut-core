package io.micronaut.json.tree;

import com.fasterxml.jackson.core.*;

import java.io.IOException;

class JsonMissing extends JsonScalar {
    static final JsonMissing INSTANCE = new JsonMissing();

    private JsonMissing() {}

    @Override
    public JsonToken asToken() {
        return JsonToken.NOT_AVAILABLE;
    }

    @Override
    public JsonParser.NumberType numberType() {
        return null;
    }

    @Override
    public boolean isValueNode() {
        return false;
    }

    @Override
    public boolean isMissingNode() {
        return true;
    }

    @Override
    void emit(JsonGenerator generator) throws IOException {
        generator.writeNull();
    }
}
