package com.computerstore.computer_store_backend.identity.bootstrap;

/** Only fixed messages may cross the CLI boundary; never retain a persistence exception as cause. */
public final class AdminBootstrapException extends RuntimeException {
    public enum Reason {
        INVALID_INPUT, UNSAFE_DATABASE_TARGET, MISSING_DATABASE_CREDENTIALS,
        ADMIN_ROLE_MISSING, ADMIN_ALREADY_EXISTS, USERNAME_EXISTS, FAILED
    }

    private final Reason reason;

    public AdminBootstrapException(Reason reason) {
        super("Admin bootstrap stopped: " + reason.name() + ".");
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
