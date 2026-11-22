package com.computerstore.computer_store_backend.operations.supplier.v1;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.flywaydb.core.internal.resource.classpath.ClassPathResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupplierPermissionMigrationRunnerTest {
    @Test void stageRequiresAllThreeExactFlagsAndSelectsOneTarget() {
        var stageA = SupplierPermissionMigrationRunner.stage(flags("A"));
        var stageB = SupplierPermissionMigrationRunner.stage(flags("B"));
        assertEquals(new SupplierPermissionMigrationRunner.Stage(5, 6), stageA);
        assertEquals(new SupplierPermissionMigrationRunner.Stage(6, 7), stageB);
        assertThrows(IllegalArgumentException.class, () -> SupplierPermissionMigrationRunner.stage(new String[0]));
        var wrongTarget = flags("A");
        wrongTarget[1] = "--confirm-target=computer_store";
        assertThrows(IllegalArgumentException.class, () -> SupplierPermissionMigrationRunner.stage(wrongTarget));
        assertThrows(IllegalArgumentException.class, () -> SupplierPermissionMigrationRunner.stage(flags("C")));
    }

    @Test void onlyManualRunnerAddsSupplierLocationAndKeepsFlywayGuards() {
        var configuration = SupplierPermissionMigrationRunner.configuration(
                SupplierPermissionMigrationRunner.stage(flags("B")), new JavaMigration[0]);
        assertEquals(List.of("classpath:db/migration", "classpath:operations/supplier/v1"),
                Arrays.stream(configuration.getLocations()).map(location -> location.getDescriptor()).toList());
        assertEquals("7", configuration.getTarget().getVersion());
        assertEquals("computer_store_dev", configuration.getDefaultSchema());
        assertTrue(configuration.isValidateOnMigrate());
        assertTrue(configuration.isCleanDisabled());
        assertFalse(configuration.isBaselineOnMigrate());
        assertFalse(configuration.isOutOfOrder());
        assertFalse(configuration.isCreateSchemas());
    }

    @Test void allSevenMigrationSourcesRetainTheirVersionsAndChecksums() throws Exception {
        var previous = SupplierPermissionMigrationRunner.previousMigrations();
        assertEquals(List.of("2", "3", "4", "5"),
                Arrays.stream(previous).map(migration -> migration.getVersion().getVersion()).toList());
        assertEquals(List.of(947719693, 253774843, -1732158983, 155246154),
                Arrays.stream(previous).map(JavaMigration::getChecksum).toList());
        assertSqlChecksum("db/migration", "V1__create_initial_schema.sql", -1142478933);
        assertSqlChecksum("operations/supplier/v1", "V6__register_supplier_permissions.sql", -1416705504);
        assertSqlChecksum("operations/supplier/v1", "V7__grant_supplier_permissions.sql", 982398228);
    }

    private void assertSqlChecksum(String location, String file, int expected) {
        var resource = new ClassPathResource(new Location("classpath:" + location),
                location + "/" + file, getClass().getClassLoader(), StandardCharsets.UTF_8);
        assertEquals(expected, ChecksumCalculator.calculate(resource));
    }

    @Test void onlyTheSelectedStageMayBePendingAndEveryChecksumMustMatch() {
        var stageA = SupplierPermissionMigrationRunner.stage(flags("A"));
        var stageB = SupplierPermissionMigrationRunner.stage(flags("B"));
        assertDoesNotThrow(() -> SupplierPermissionMigrationRunner.verifyResolved(history(stageA), stageA));
        assertDoesNotThrow(() -> SupplierPermissionMigrationRunner.verifyResolved(history(stageB), stageB));
        var wrongDescription = history(stageA);
        when(wrongDescription[5].getDescription()).thenReturn("unexpected description");
        assertThrows(IllegalStateException.class, () -> SupplierPermissionMigrationRunner.verifyResolved(wrongDescription, stageA));
        var wrongChecksum = history(stageA);
        when(wrongChecksum[5].getResolvedChecksum()).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> SupplierPermissionMigrationRunner.verifyResolved(wrongChecksum, stageA));
        assertThrows(IllegalStateException.class, () -> SupplierPermissionMigrationRunner.verifyResolved(history(stageA), stageB));
        assertThrows(IllegalStateException.class, () -> SupplierPermissionMigrationRunner.verifyResolved(Arrays.copyOf(history(stageA), 8), stageA));
    }

    private static String[] flags(String stage) {
        return new String[]{"--supplier-permission-rollout", "--confirm-target=computer_store_dev", "--stage=" + stage};
    }

    private static MigrationInfo[] history(SupplierPermissionMigrationRunner.Stage stage) {
        var checksums = List.of(-1142478933, 947719693, 253774843, -1732158983, 155246154, -1416705504, 982398228);
        var descriptions = List.of("create initial schema", "register category permissions", "grant category permissions",
                "register product permissions", "grant product permissions", "register supplier permissions", "grant supplier permissions");
        var entries = new MigrationInfo[checksums.size()];
        for (int index = 0; index < entries.length; index++) {
            var entry = mock(MigrationInfo.class);
            int version = index + 1;
            when(entry.getVersion()).thenReturn(MigrationVersion.fromVersion(Integer.toString(version)));
            when(entry.getDescription()).thenReturn(descriptions.get(index));
            when(entry.getResolvedChecksum()).thenReturn(checksums.get(index));
            when(entry.getState()).thenReturn(version <= stage.before() ? MigrationState.SUCCESS
                    : version == stage.target() ? MigrationState.PENDING : MigrationState.ABOVE_TARGET);
            entries[index] = entry;
        }
        return entries;
    }
}
