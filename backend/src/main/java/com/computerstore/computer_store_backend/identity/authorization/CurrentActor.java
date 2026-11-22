package com.computerstore.computer_store_backend.identity.authorization;

/** Internal actor identity, not a request DTO. Contains no entity, credentials or session state. */
public record CurrentActor(Long userId, String username) { }
