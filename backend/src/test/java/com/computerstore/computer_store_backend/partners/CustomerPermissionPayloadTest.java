package com.computerstore.computer_store_backend.partners;

import com.computerstore.computer_store_backend.identity.authorization.PermissionCode;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.flywaydb.core.internal.resource.classpath.ClassPathResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomerPermissionPayloadTest {
    private static final String PATH = "operations/customer/v1/";
    private static final Set<String> CODES = Set.of("CUSTOMER_READ", "CUSTOMER_CREATE", "CUSTOMER_UPDATE");

    @Test void catalogRegistersExactlyTheThreeEligibleCodesWithIdenticalNames() throws Exception {
        var sql = load("V8__register_customer_permissions.sql");
        var rows = Pattern.compile("SELECT '(CUSTOMER_[A-Z_]+)'").matcher(sql).results().toList();
        assertEquals(3, rows.size());
        assertEquals(CODES, rows.stream().map(row -> row.group(1)).collect(Collectors.toSet()));
        for (var code : CODES) assertEquals(PermissionCode.Status.ELIGIBLE_PROPOSED, PermissionCode.fromCode(code).orElseThrow().status());
        assertTrue(sql.contains("INSERT INTO permission "));
        assertTrue(sql.contains("desired.code, NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)"));
    }

    @Test void grantsAreExactlyFourProposedPairsAndAreOutsideDefaultFlywayDiscovery() throws Exception {
        var sql = load("V9__grant_customer_permissions.sql");
        var rows = Pattern.compile("SELECT '([A-Z_]+)'(?: AS role_code)?, '(CUSTOMER_[A-Z_]+)'").matcher(sql).results().toList();
        assertEquals(4, rows.size());
        assertEquals(Set.of("MANAGER:CUSTOMER_READ", "SALES_STAFF:CUSTOMER_CREATE",
                "SALES_STAFF:CUSTOMER_UPDATE", "SALES_STAFF:CUSTOMER_READ"), rows.stream()
                .map(row -> row.group(1) + ":" + row.group(2)).collect(Collectors.toSet()));
        assertTrue(sql.contains("INSERT INTO role_permission "));
        assertTrue(sql.contains("CAST(name AS BINARY) = CAST(code AS BINARY) AND description IS NULL"));
        assertTrue(loadAt("application.properties").contains("spring.flyway.locations=classpath:db/migration"));
        assertNull(getClass().getClassLoader().getResource("db/migration/V8__register_customer_permissions.sql"));
        assertNull(getClass().getClassLoader().getResource("db/migration/V9__grant_customer_permissions.sql"));
    }

    @Test void checksumsAndStageOrderAreStable() throws Exception {
        assertChecksum("V8__register_customer_permissions.sql", 151122022);
        assertChecksum("V9__grant_customer_permissions.sql", 985142827);
        assertTrue(load("V8__register_customer_permissions.sql").contains("version = '7' AND type = 'SQL' AND checksum = 982398228"));
        assertTrue(load("V9__grant_customer_permissions.sql").contains("version = '8' AND success = 1 AND type = 'SQL' AND checksum = 151122022"));
    }

    private void assertChecksum(String file, int checksum) {
        var resource = new ClassPathResource(new Location("classpath:" + PATH), PATH + file,
                getClass().getClassLoader(), StandardCharsets.UTF_8);
        assertEquals(checksum, ChecksumCalculator.calculate(resource));
    }

    private String load(String name) throws Exception { return loadAt(PATH + name); }

    private String loadAt(String name) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
