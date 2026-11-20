package com.computerstore.computer_store_backend.identity.bootstrap;

import com.computerstore.computer_store_backend.shared.validation.IdentifierNormalizer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.security.crypto.password.PasswordEncoder;
import static com.computerstore.computer_store_backend.identity.bootstrap.AdminBootstrapException.Reason.INVALID_INPUT;

/** Not a record: generated toString/accessors must not expose credentials. */
public final class AdminBootstrapRequest implements AutoCloseable {
    private final String username;
    private final String fullName;
    private final char[] password;
    private boolean closed;

    public AdminBootstrapRequest(String username, String fullName, char[] password) {
        String normalized;
        try { normalized = IdentifierNormalizer.username(username); }
        catch (RuntimeException ignored) { throw new AdminBootstrapException(INVALID_INPUT); }
        if (normalized == null || normalized.isEmpty() || normalized.length() > 100
                || fullName == null || fullName.strip().isEmpty()
                || fullName.strip().codePointCount(0, fullName.strip().length()) > 150
                || !StandardCharsets.UTF_8.newEncoder().canEncode(fullName)
                || fullName.codePoints().anyMatch(Character::isISOControl)
                || password == null || password.length == 0
                || CharBuffer.wrap(password).chars().allMatch(Character::isWhitespace)) {
            throw new AdminBootstrapException(INVALID_INPUT);
        }
        // BCrypt accepts at most 72 UTF-8 bytes. Never trim or normalize a password.
        try {
            var bytes = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password));
            try {
                if (bytes.remaining() > 72) throw new AdminBootstrapException(INVALID_INPUT);
            } finally {
                bytes.clear();
                while (bytes.hasRemaining()) bytes.put((byte) 0);
            }
        } catch (CharacterCodingException ignored) {
            throw new AdminBootstrapException(INVALID_INPUT);
        }
        this.username = normalized;
        this.fullName = fullName.strip();
        this.password = password.clone();
    }

    public String username() { return username; }
    public String fullName() { return fullName; }

    public String encodePassword(PasswordEncoder encoder) {
        if (closed) throw new AdminBootstrapException(INVALID_INPUT);
        return encoder.encode(CharBuffer.wrap(password));
    }

    public boolean passwordEquals(String other) {
        return !closed && other != null && other.contentEquals(CharBuffer.wrap(password));
    }

    @Override public void close() { Arrays.fill(password, '\0'); closed = true; }
    @Override public String toString() { return "AdminBootstrapRequest[redacted]"; }
}
