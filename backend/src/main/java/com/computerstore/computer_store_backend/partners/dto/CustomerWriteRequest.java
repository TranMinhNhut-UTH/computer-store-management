package com.computerstore.computer_store_backend.partners.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.databind.JsonNode;

public final class CustomerWriteRequest {
    @NotBlank private final String code;
    @NotBlank private final String fullName;
    private final Set<String> suppliedContacts = new HashSet<>();
    @CodePointLength(max = 30) private String phone;
    @Email @CodePointLength(max = 254) private String email;
    @CodePointLength(max = 500) private String address;
    private String notes;

    @JsonCreator
    public CustomerWriteRequest(@JsonProperty("code") String code, @JsonProperty("fullName") String fullName) {
        this.code = code;
        this.fullName = fullName;
    }

    public String code() { return code; }
    public String fullName() { return fullName; }
    public String phone() { return phone; }
    public String email() { return email; }
    public String address() { return address; }
    public String notes() { return notes; }
    public boolean supplies(String field) { return suppliedContacts.contains(field); }

    @JsonSetter("phone") void setPhone(JsonNode value) { phone = contact("phone", value); }
    @JsonSetter("email") void setEmail(JsonNode value) { email = contact("email", value); }
    @JsonSetter("address") void setAddress(JsonNode value) { address = contact("address", value); }
    @JsonSetter("notes") void setNotes(JsonNode value) { notes = contact("notes", value); }

    private String contact(String field, JsonNode value) {
        suppliedContacts.add(field);
        if (value == null || value.isNull()) return null;
        if (!value.isString()) throw new IllegalArgumentException("Customer contact fields must be strings or null.");
        var text = value.asString().strip();
        return text.isEmpty() ? null : text;
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported customer request field.");
    }
}
