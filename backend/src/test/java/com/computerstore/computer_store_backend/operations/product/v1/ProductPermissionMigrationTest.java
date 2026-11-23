package com.computerstore.computer_store_backend.operations.product.v1;

import com.computerstore.computer_store_backend.identity.authorization.registration.ProductPermissionRollout;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import static com.computerstore.computer_store_backend.operations.product.v1.FrozenProductRolloutV1.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductPermissionMigrationTest {
    private static final LocalDateTime OLD = LocalDateTime.parse("2026-11-22T01:00:00");
    private static final LocalDateTime NOW = OLD.plusHours(2);
    private static final List<String> DESCRIPTIONS = List.of("create initial schema", "register category permissions",
            "grant category permissions", "register product permissions", "grant product permissions");
    private static final List<Integer> CHECKSUMS = List.of(-1142478933, 947719693, 253774843, -1732158983, 155246154);

    @Test void exactFrozenPayloadMatchesApprovalAndPreservesOtherModules() throws Exception {
        var jdbc = new Jdbc();
        assertEquals(CODES, ProductPermissionRollout.approvedRegistration().entries().stream()
                .map(entry -> entry.code()).toList());
        assertEquals(Set.copyOf(PAIRS), ProductPermissionRollout.approvedGrants().stream()
                .map(grant -> grant.role().name() + ":" + grant.permission().code()).collect(Collectors.toSet()));
        assertEquals(5, CODES.size());
        assertEquals(7, PAIRS.size());
        assertFalse(PAIRS.stream().anyMatch(pair -> pair.startsWith("ADMIN:") || pair.equals("WAREHOUSE_STAFF:PRODUCT_UPDATE")
                || pair.equals("WAREHOUSE_STAFF:PRODUCT_CREATE")));

        var otherPermission = Map.copyOf(jdbc.permissions.getFirst());
        var otherGrant = Map.copyOf(jdbc.grants.getFirst());
        jdbc.a();
        assertEquals(List.of(INSERT_PERMISSION, INSERT_PERMISSION, INSERT_PERMISSION, INSERT_PERMISSION,
                INSERT_PERMISSION), jdbc.prepared);
        assertEquals(Set.copyOf(CODES), jdbc.productCodes());
        assertEquals(List.of(otherGrant), jdbc.grants);
        assertTrue(jdbc.permissions.stream().skip(1).allMatch(row -> row.get("code").equals(row.get("name"))
                && row.get("description") == null));
        jdbc.applied(4);
        jdbc.prepared.clear();
        jdbc.b();
        assertEquals(7, jdbc.prepared.size());
        assertTrue(jdbc.prepared.stream().allMatch(INSERT_GRANT::equals));
        assertEquals(Set.copyOf(PAIRS), jdbc.productPairs());
        assertEquals(otherPermission, jdbc.permissions.getFirst());
        assertEquals(otherGrant, jdbc.grants.getFirst());
        jdbc.applied(5);
        verifyState(jdbc.connection, 5, true);
        jdbc.noOwnership();
    }

    @Test void stagedAdoptionIsInsertOnlyAndRerunsDoNotDuplicate() throws Exception {
        var jdbc = new Jdbc();
        var existingPermission = permission(88L, "PRODUCT_READ", "PRODUCT_READ", null, OLD);
        var existingGrant = grant(77L, 12L, 88L, OLD);
        jdbc.permissions.add(existingPermission);
        jdbc.grants.add(existingGrant);
        jdbc.a(); jdbc.a(); // Model compatible rows before Flyway records successful V4.
        assertEquals(4, jdbc.prepared.size());
        jdbc.applied(4);
        verifyState(jdbc.connection, 4, true);
        jdbc.prepared.clear();
        jdbc.b(); jdbc.b(); // Model compatible rows before Flyway records successful V5.
        assertEquals(6, jdbc.prepared.size());
        assertTrue(jdbc.permissions.contains(existingPermission));
        assertTrue(jdbc.grants.contains(existingGrant));
        jdbc.applied(5);
        verifyState(jdbc.connection, 5, true);
        assertThrows(SQLException.class, () -> verifyState(jdbc.connection, 4, false));
        assertThrows(SQLException.class, jdbc::b); // A recorded success is verified, never reapplied.
        jdbc.noOwnership();
    }

    @Test void conflictsAbortBeforeAnyWrite() throws Exception {
        for (var row : List.of(permission(88L, "PRODUCT_READ", "Unexpected", null, OLD),
                permission(88L, "PRODUCT_READ", "PRODUCT_READ", "", OLD),
                permission(88L, "PRODUCT_UNKNOWN", "PRODUCT_UNKNOWN", null, OLD),
                permission(88L, "product_read", "product_read", null, OLD))) {
            var jdbc = new Jdbc();
            jdbc.permissions.add(row);
            assertThrows(SQLException.class, jdbc::a);
            assertTrue(jdbc.prepared.isEmpty());
        }
        for (long role : List.of(11L, 14L, 99L)) {
            var jdbc = new Jdbc();
            jdbc.permissions.add(permission(88L, "PRODUCT_UPDATE", "PRODUCT_UPDATE", null, OLD));
            jdbc.grants.add(grant(77L, role, 88L, OLD));
            assertThrows(SQLException.class, jdbc::a);
            assertTrue(jdbc.prepared.isEmpty());
        }
        var duplicate = new Jdbc();
        duplicate.grants.add(grant(88L, 12L, 55L, OLD));
        assertThrows(SQLException.class, duplicate::a);
        assertTrue(duplicate.prepared.isEmpty());
    }

    @Test void targetHistoryAndTransactionFailuresCannotWrite() throws Exception {
        var wrongTarget = new Jdbc();
        wrongTarget.schema = "computer_store";
        assertThrows(SQLException.class, wrongTarget::a);
        assertTrue(wrongTarget.prepared.isEmpty());

        var wrongMetadata = new Jdbc();
        when(wrongMetadata.metadata.getURL()).thenReturn("jdbc:mysql://127.0.0.1:3307/computer_store_dev");
        assertThrows(SQLException.class, wrongMetadata::a);
        assertTrue(wrongMetadata.prepared.isEmpty());

        var badEngine = new Jdbc();
        badEngine.engines.getFirst().put("ENGINE", "MyISAM");
        assertThrows(SQLException.class, badEngine::a);
        assertTrue(badEngine.prepared.isEmpty());

        for (String key : List.of("type", "description", "checksum", "version", "success")) {
            var conflict = new Jdbc();
            conflict.history.getLast().put(key, key.equals("success") ? false : "collision");
            assertThrows(SQLException.class, conflict::a);
            assertTrue(conflict.prepared.isEmpty());
        }
        var badV1 = new Jdbc();
        badV1.history.getFirst().put("checksum", 3);
        assertThrows(SQLException.class, badV1::a);
        assertTrue(badV1.prepared.isEmpty());

        var missing = new Jdbc();
        assertThrows(SQLException.class, missing::b);
        missing.applied(4);
        assertThrows(SQLException.class, missing::b);
        assertTrue(missing.prepared.isEmpty());

        var occupied = new Jdbc();
        occupied.applied(4);
        occupied.history.getLast().put("checksum", 0);
        assertThrows(SQLException.class, () -> verifyState(occupied.connection, 4, false));
        assertTrue(occupied.prepared.isEmpty());

        var completedButDrifted = new Jdbc();
        completedButDrifted.applied(4);
        assertThrows(SQLException.class, () -> verifyState(completedButDrifted.connection, 4, true));

        var tx = new Jdbc();
        when(tx.connection.getAutoCommit()).thenReturn(true);
        assertThrows(SQLException.class, tx::a);
        when(tx.connection.getAutoCommit()).thenReturn(false);
        when(tx.connection.isReadOnly()).thenReturn(true);
        assertThrows(SQLException.class, tx::a);
        when(tx.connection.isReadOnly()).thenReturn(false);
        when(tx.connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        assertThrows(SQLException.class, tx::a);
        assertTrue(tx.prepared.isEmpty());
    }

    @Test void stageFailureAndPostconditionFailurePropagateToTheTransactionOwner() throws Exception {
        var jdbc = new Jdbc();
        jdbc.a();
        jdbc.applied(4);
        jdbc.writes = 0;
        jdbc.failAt = 2;
        assertSame(jdbc.failure, assertThrows(SQLException.class, jdbc::b));
        assertEquals(2, jdbc.writes);
        jdbc.noOwnership();

        var drift = new Jdbc();
        drift.afterWrite = () -> drift.permissions.getFirst().put("name", "Altered by fixture");
        assertThrows(SQLException.class, drift::a);
        drift.noOwnership();

        var zero = new Jdbc();
        zero.affected = 0;
        assertThrows(SQLException.class, zero::a);
        zero.noOwnership();
    }

    private static Map<String, Object> row(Object... fields) {
        var result = new HashMap<String, Object>();
        for (int i = 0; i < fields.length; i += 2) result.put((String) fields[i], fields[i + 1]);
        return result;
    }

    private static Map<String, Object> permission(long id, String code, String name, String description, LocalDateTime time) {
        return row("id", id, "code", code, "name", name, "description", description, "created_at", time, "updated_at", time);
    }

    private static Map<String, Object> grant(long id, long role, long permission, LocalDateTime time) {
        return row("id", id, "role_id", role, "permission_id", permission, "created_at", time);
    }

    private static final class Jdbc {
        final Connection connection = mock(Connection.class);
        final DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        final Context context = mock(Context.class);
        final List<Map<String, Object>> permissions = new ArrayList<>(List.of(permission(55L, "CATEGORY_READ", "CATEGORY_READ", "existing", OLD)));
        final List<Map<String, Object>> grants = new ArrayList<>(List.of(grant(66L, 12L, 55L, OLD)));
        final List<Map<String, Object>> history = new ArrayList<>();
        final List<Map<String, Object>> roles = List.of(row("id", 11L, "code", "ADMIN"), row("id", 12L, "code", "MANAGER"),
                row("id", 13L, "code", "SALES_STAFF"), row("id", 14L, "code", "WAREHOUSE_STAFF"));
        final List<Map<String, Object>> engines = List.of("app_role", "permission", "role_permission", "flyway_schema_history")
                .stream().map(table -> row("TABLE_NAME", table, "ENGINE", "InnoDB")).toList();
        final List<String> prepared = new ArrayList<>();
        final SQLException failure = new SQLException("injected failure");
        String schema = SCHEMA;
        int writes, failAt = -1, affected = 1;
        Runnable afterWrite = () -> { };

        Jdbc() throws Exception {
            for (int v = 1; v <= 3; v++) applied(v);
            when(context.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.getURL()).thenReturn(URL);
            when(metadata.getDatabaseProductName()).thenReturn("MySQL");
            when(metadata.supportsTransactions()).thenReturn(true);
            when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_REPEATABLE_READ);
            when(connection.createStatement()).thenAnswer(call -> {
                var statement = mock(Statement.class);
                when(statement.executeQuery(anyString())).thenAnswer(query -> {
                    String sql = query.getArgument(0);
                    return rows(switch (sql) {
                        case TARGET -> List.of(row("schema", schema));
                        case ENGINES -> engines;
                        case HISTORY -> history;
                        case READ_ROLES, READ_ROLES + LOCK -> roles;
                        case READ_PERMISSIONS, READ_PERMISSIONS + LOCK -> permissions;
                        case READ_GRANTS, READ_GRANTS + LOCK -> grants;
                        default -> throw new AssertionError("Unexpected query: " + sql);
                    });
                });
                return statement;
            });
            when(connection.prepareStatement(anyString())).thenAnswer(call -> {
                String sql = call.getArgument(0);
                prepared.add(sql);
                var bound = new HashMap<Integer, Object>();
                var statement = mock(PreparedStatement.class);
                doAnswer(a -> { bound.put(a.getArgument(0), a.getArgument(1)); return null; }).when(statement).setString(anyInt(), anyString());
                doAnswer(a -> { bound.put(a.getArgument(0), a.getArgument(1)); return null; }).when(statement).setLong(anyInt(), anyLong());
                when(statement.executeUpdate()).thenAnswer(a -> {
                    if (++writes == failAt) throw failure;
                    if (affected == 0) return 0;
                    switch (sql) {
                        case INSERT_PERMISSION -> permissions.add(permission(1000L + permissions.size(),
                                (String) bound.get(1), (String) bound.get(2), null, NOW));
                        case INSERT_GRANT -> grants.add(grant(2000L + grants.size(), (long) bound.get(1), (long) bound.get(2), NOW));
                        default -> throw new AssertionError("Unexpected write: " + sql);
                    }
                    afterWrite.run();
                    return 1;
                });
                return statement;
            });
        }

        void a() throws Exception { new V4__register_product_permissions().migrate(context); }
        void b() throws Exception { new V5__grant_product_permissions().migrate(context); }
        void applied(int v) {
            history.add(row("version", Integer.toString(v), "description", DESCRIPTIONS.get(v - 1),
                    "type", v == 1 ? "SQL" : "JDBC", "checksum", CHECKSUMS.get(v - 1), "success", true));
        }
        Set<String> productCodes() {
            return permissions.stream().map(row -> (String) row.get("code")).filter(code -> code.startsWith("PRODUCT_"))
                    .collect(Collectors.toSet());
        }
        Set<String> productPairs() {
            var pairs = new HashSet<String>();
            for (var grant : grants) {
                String role = (String) roles.stream().filter(row -> row.get("id").equals(grant.get("role_id"))).findFirst().orElseThrow().get("code");
                String code = (String) permissions.stream().filter(row -> row.get("id").equals(grant.get("permission_id"))).findFirst().orElseThrow().get("code");
                if (code.startsWith("PRODUCT_")) pairs.add(role + ":" + code);
            }
            return pairs;
        }
        void noOwnership() throws SQLException {
            verify(connection, never()).commit();
            verify(connection, never()).rollback();
            verify(connection, never()).setAutoCommit(anyBoolean());
            verify(connection, never()).close();
        }

        private static ResultSet rows(List<Map<String, Object>> data) throws SQLException {
            var snapshot = new ArrayList<>(data);
            int[] index = {-1};
            var result = mock(ResultSet.class);
            when(result.next()).thenAnswer(a -> ++index[0] < snapshot.size());
            when(result.getString(1)).thenAnswer(a -> snapshot.get(index[0]).get("schema"));
            when(result.getString(anyString())).thenAnswer(a -> snapshot.get(index[0]).get(a.getArgument(0)));
            when(result.getLong(anyString())).thenAnswer(a -> snapshot.get(index[0]).get(a.getArgument(0)));
            when(result.getBoolean(anyString())).thenAnswer(a -> snapshot.get(index[0]).get(a.getArgument(0)));
            when(result.getObject(anyString())).thenAnswer(a -> snapshot.get(index[0]).get(a.getArgument(0)));
            when(result.getObject(anyString(), eq(LocalDateTime.class))).thenAnswer(a -> snapshot.get(index[0]).get(a.getArgument(0)));
            return result;
        }
    }
}
