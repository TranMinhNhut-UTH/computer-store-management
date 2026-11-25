package com.computerstore.computer_store_backend.catalog.dto.request;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Accepts only a JSON string for the required sellingPrice field. */
public final class ProductPriceStringDeserializer extends ValueDeserializer<String> {
    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.hasToken(JsonToken.VALUE_STRING)) return parser.getString();
        // Let the required field's Bean Validation report the error on sellingPrice.
        parser.skipChildren();
        return null;
    }
}
