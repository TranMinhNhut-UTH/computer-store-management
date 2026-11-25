package com.computerstore.computer_store_backend.identity.authorization;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Internal actor identity. The session version binds subsequent permission queries to this check. */
public record CurrentActor(Long userId, String username, @JsonIgnore long sessionVersion) {
    public CurrentActor(Long userId, String username) { this(userId, username, 0L); }
}
