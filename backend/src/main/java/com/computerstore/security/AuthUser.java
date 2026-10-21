package com.computerstore.security;

/** Principal được gắn vào SecurityContext sau khi xác thực JWT. */
public record AuthUser(long id, String role) {}
