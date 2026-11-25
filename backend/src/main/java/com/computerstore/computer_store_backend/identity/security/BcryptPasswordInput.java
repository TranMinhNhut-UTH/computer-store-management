package com.computerstore.computer_store_backend.identity.security;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

/** Password input bounds common to authentication and account provisioning. */
public final class BcryptPasswordInput {
    private static final int MAX_UTF8_BYTES = 72;

    private BcryptPasswordInput() {}

    public static boolean isValid(CharSequence password) {
        if (password == null || password.length() == 0 || password.length() > MAX_UTF8_BYTES
                || password.chars().allMatch(Character::isWhitespace)) return false;
        try {
            var encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password));
            try {
                return encoded.remaining() <= MAX_UTF8_BYTES;
            } finally {
                encoded.clear();
                while (encoded.hasRemaining()) encoded.put((byte) 0);
            }
        } catch (CharacterCodingException invalid) {
            return false;
        }
    }
}
