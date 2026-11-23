package com.computerstore.computer_store_backend.operations.session.v1;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionVersionMigrationRunnerTest {
    @Test void requiresBothFlagsAndExactLoopbackSchema() {
        var flags = new String[]{"--apply-p01-session-version", "--confirm-target=computer_store_dev"};
        String url = "jdbc:mysql://127.0.0.1:3306/computer_store_dev";
        assertDoesNotThrow(() -> SessionVersionMigrationRunner.requireApproval(flags, url));
        assertThrows(IllegalArgumentException.class, () -> SessionVersionMigrationRunner.requireApproval(
                new String[]{flags[0]}, url));
        assertThrows(IllegalArgumentException.class, () -> SessionVersionMigrationRunner.requireApproval(
                new String[]{flags[0], flags[0]}, url));
        for (String other : new String[]{null, url + "?createDatabaseIfNotExist=true",
                url.replace("127.0.0.1", "localhost"), url.replace("computer_store_dev", "computer_store")}) {
            assertThrows(IllegalArgumentException.class, () -> SessionVersionMigrationRunner.requireApproval(flags, other));
        }
    }
}
