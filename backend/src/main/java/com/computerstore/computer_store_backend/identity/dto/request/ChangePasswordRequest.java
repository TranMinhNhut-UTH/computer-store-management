package com.computerstore.computer_store_backend.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ChangePasswordRequest(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @JsonDeserialize(using = PasswordCharacters.class) char[] currentPassword,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @JsonDeserialize(using = PasswordCharacters.class) char[] newPassword) implements AutoCloseable {

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        close();
        throw new IllegalArgumentException("Unsupported password-change field.");
    }

    @Override public void close() {
        if (currentPassword != null) Arrays.fill(currentPassword, '\0');
        if (newPassword != null) Arrays.fill(newPassword, '\0');
    }

    @Override public String toString() { return "ChangePasswordRequest[redacted]"; }

    public static final class PasswordCharacters extends ValueDeserializer<char[]> {
        @Override public char[] deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return context.reportInputMismatch(char[].class, "Expected a password string.");
            }
            int length = parser.getStringLength();
            // More than 72 UTF-16 units cannot fit in a 72-byte UTF-8 password.
            if (length > 72) return context.reportInputMismatch(char[].class, "Password exceeds the input limit.");
            int start = parser.getStringOffset();
            return Arrays.copyOfRange(parser.getStringCharacters(), start, start + length);
        }
    }
}
