package com.computerstore.computer_store_backend.identity.bootstrap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

class AdminBootstrapInputTest {
    @Test void validatesAndNormalizesIdentityWithoutChangingPassword() {
        String password = " " + UUID.randomUUID() + " ";
        try (var input = new AdminBootstrapRequest(" \tOPERATOR\r\n", " Nguyễn Văn An ", password.toCharArray())) {
            assertEquals("operator", input.username());
            assertEquals("Nguyễn Văn An", input.fullName());
            var encoder = new BCryptPasswordEncoder();
            assertTrue(encoder.matches(password, input.encodePassword(encoder)));
            assertFalse(encoder.matches(password.strip(), input.encodePassword(encoder)));
            assertEquals("AdminBootstrapRequest[redacted]", input.toString());
        }
    }

    @Test void rejectsInvalidUsernameWithoutEchoingInput() {
        for (String username : new String[]{null, "", " \t", "tên", "a b", "x@y", "a".repeat(101)}) {
            assertThrows(AdminBootstrapException.class, () -> new AdminBootstrapRequest(username, "Employee", UUID.randomUUID().toString().toCharArray()));
        }
    }

    @Test void requiresRealFullNameWithinSchemaLength() {
        for (String name : new String[]{null, "", "  ", "x".repeat(151), "Line\nBreak", "Name\uD800"}) {
            assertThrows(AdminBootstrapException.class, () -> new AdminBootstrapRequest("operator", name, UUID.randomUUID().toString().toCharArray()));
        }
    }

    @Test void rejectsBlankMalformedOrOver72BytePasswords() {
        for (char[] password : new char[][]{null, new char[0], " \t".toCharArray(), "x".repeat(73).toCharArray(),
                "ế".repeat(25).toCharArray(), new char[]{'x', '\uD800'}}) {
            var failure = assertThrows(AdminBootstrapException.class, () -> new AdminBootstrapRequest("operator", "Employee", password));
            assertEquals(AdminBootstrapException.Reason.INVALID_INPUT, failure.reason());
            assertNull(failure.getCause());
        }
    }

    @Test void closedInputCannotHashOrMatchAPassword() {
        var input = new AdminBootstrapRequest("operator", "Employee", UUID.randomUUID().toString().toCharArray());
        input.close();
        assertThrows(AdminBootstrapException.class, () -> input.encodePassword(new BCryptPasswordEncoder()));
        assertFalse(input.passwordEquals("anything"));
    }

    @Test void permitsOnlyLocalMysqlAndExactCatalogWithSafeOptions() {
        for (String host : new String[]{"localhost", "127.0.0.1:3306", "[::1]:3307"}) {
            var target = BootstrapDatabaseTarget.fromEnvironment(environment("jdbc:mysql://" + host + "/computer_store_dev?serverTimezone=UTC"));
            assertEquals("BootstrapDatabaseTarget[redacted]", target.toString());
        }
    }

    @Test void rejectsAmbiguousOrRemoteJdbcTargetsAndDangerousOptions() {
        for (String url : new String[]{"", "jdbc:postgresql://localhost/computer_store_dev", "jdbc:mysql://remote/computer_store_dev",
                "jdbc:mysql://localhost/computer_store", "jdbc:mysql://localhost/computer_store_dev/extra",
                "jdbc:mysql://localhost:65536/computer_store_dev", "jdbc:mysql://user:secret@localhost/computer_store_dev",
                "jdbc:mysql://localhost,remote/computer_store_dev", "jdbc:mysql://localhost/computer_store_dev#other",
                "jdbc:mysql://localhost/computer_store_dev?databaseName=other", "jdbc:mysql://localhost/computer_store_dev?password=secret",
                "jdbc:mysql://localhost/computer_store_dev?sessionVariables=sql_log_bin=0",
                "jdbc:mysql://localhost/computer_store_dev?serverTimezone=UTC&serverTimezone=UTC",
                "jdbc:mysql://localhost/computer_store_dev?useSSL=false&"}) {
            var failure = assertThrows(AdminBootstrapException.class, () -> BootstrapDatabaseTarget.fromEnvironment(environment(url)));
            assertEquals(AdminBootstrapException.Reason.UNSAFE_DATABASE_TARGET, failure.reason());
            assertFalse(failure.toString().contains(url.isEmpty() ? "jdbc:" : url));
        }
    }

    @Test void requiresBothDatabaseCredentialsAndUrl() {
        assertThrows(AdminBootstrapException.class, () -> BootstrapDatabaseTarget.fromEnvironment(Map.of()));
        for (String key : new String[]{"DB_USERNAME", "DB_PASSWORD"}) {
            var env = environment("jdbc:mysql://localhost/computer_store_dev");
            env.remove(key);
            assertThrows(AdminBootstrapException.class, () -> BootstrapDatabaseTarget.fromEnvironment(env));
            env.put(key, " ");
            assertThrows(AdminBootstrapException.class, () -> BootstrapDatabaseTarget.fromEnvironment(env));
        }
    }

    private Map<String, String> environment(String url) {
        return new HashMap<>(Map.of("DB_URL", url, "DB_USERNAME", "local_operator", "DB_PASSWORD", UUID.randomUUID().toString()));
    }
}
