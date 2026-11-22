package com.computerstore.computer_store_backend.partners;

import com.computerstore.computer_store_backend.identity.authorization.PermissionCode;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SupplierPermissionPayloadTest {
    private static final String PATH = "operations/supplier/v1/";
    private static final Set<String> CODES = Set.of("SUPPLIER_READ", "SUPPLIER_CREATE", "SUPPLIER_UPDATE", "SUPPLIER_DEACTIVATE");

    @Test void catalogRegistersExactlyTheFourEligibleCodesWithIdenticalNames() throws Exception {
        var sql = load("V6__register_supplier_permissions.sql");
        var rows = Pattern.compile("SELECT '(SUPPLIER_[A-Z_]+)'").matcher(sql).results().toList();
        assertEquals(4, rows.size());
        assertEquals(CODES, rows.stream().map(row -> row.group(1)).collect(Collectors.toSet()));
        for (var code : CODES) assertEquals(PermissionCode.Status.ELIGIBLE_PROPOSED, PermissionCode.fromCode(code).orElseThrow().status());
        assertTrue(sql.contains("INSERT INTO permission "));
        assertTrue(sql.contains("desired.code, NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)"));
    }

    @Test void grantsAreExactlyFiveProposedPairsAndAreOutsideDefaultFlywayDiscovery() throws Exception {
        var sql = load("V7__grant_supplier_permissions.sql");
        var rows = Pattern.compile("SELECT '([A-Z_]+)'(?: AS role_code)?, '(SUPPLIER_[A-Z_]+)'").matcher(sql).results().toList();
        assertEquals(5, rows.size());
        assertEquals(Set.of("MANAGER:SUPPLIER_READ", "MANAGER:SUPPLIER_CREATE", "MANAGER:SUPPLIER_UPDATE",
                "MANAGER:SUPPLIER_DEACTIVATE", "WAREHOUSE_STAFF:SUPPLIER_READ"), rows.stream()
                .map(row -> row.group(1) + ":" + row.group(2)).collect(Collectors.toSet()));
        assertTrue(sql.contains("INSERT INTO role_permission "));
        assertTrue(sql.contains("CAST(name AS BINARY) = CAST(code AS BINARY) AND description IS NULL"));
        assertTrue(loadAt("application.properties").contains("spring.flyway.locations=classpath:db/migration"));
        assertNull(getClass().getClassLoader().getResource("db/migration/V6__register_supplier_permissions.sql"));
        assertNull(getClass().getClassLoader().getResource("db/migration/V7__grant_supplier_permissions.sql"));
    }

    private String load(String name) throws Exception { return loadAt(PATH + name); }

    private String loadAt(String name) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
