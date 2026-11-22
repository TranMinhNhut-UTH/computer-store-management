package com.computerstore.computer_store_backend.operations.category.v1;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs migration methods against in-memory JDBC stubs only, never Flyway or a real database. */
class CategoryMigrationTest {
    private static final LocalDateTime OLD = LocalDateTime.of(2026, 11, 1, 1, 2, 3);

    @Test void stageARegistersOnlyFourExactMetadataRowsAndDoesNotGrantOrChangeAccounts() throws Exception {
        var db = new Jdbc(); var users = List.copyOf(db.users);
        db.a();
        assertEquals(Set.of("CATEGORY_READ", "CATEGORY_CREATE", "CATEGORY_UPDATE", "CATEGORY_DEACTIVATE"), db.codes());
        for (var row : db.permissions) { assertEquals(row.get("code"), row.get("name")); assertNull(row.get("description")); }
        assertEquals(4, db.writes); assertTrue(db.grants.isEmpty()); assertEquals(users, db.users);
        assertTrue(db.prepared.stream().allMatch(FrozenCategoryRolloutV1.INSERT_PERMISSION::equals));
        db.noTransactionOwnership();
    }

    @Test void stageBRegistersExactlySixPairsUsingLookedUpIdsNeverAdminOrUserRoleWrites() throws Exception {
        var db = new Jdbc(); db.a(); db.v2(); db.prepared.clear(); db.writes = 0;
        var permissions = List.copyOf(db.permissions); var users = List.copyOf(db.users);
        db.b();
        assertEquals(Set.of("MANAGER:CATEGORY_READ", "MANAGER:CATEGORY_CREATE", "MANAGER:CATEGORY_UPDATE",
                "MANAGER:CATEGORY_DEACTIVATE", "SALES_STAFF:CATEGORY_READ", "WAREHOUSE_STAFF:CATEGORY_READ"), db.pairs());
        assertEquals(6, db.writes); assertEquals(permissions, db.permissions); assertEquals(users, db.users);
        assertTrue(db.grants.stream().noneMatch(row -> row.get("role_id").equals(41L)));
        assertTrue(db.prepared.stream().allMatch(FrozenCategoryRolloutV1.INSERT_GRANT::equals));
        db.noTransactionOwnership();
    }

    @Test void compatibleRowsAreIdempotentAndPreserveIdsAndBothTimestampsAcrossBothStages() throws Exception {
        var db = new Jdbc(); db.permissions.add(permission(73, "CATEGORY_READ", "CATEGORY_READ", null));
        var existing = Map.copyOf(withoutNulls(db.permissions.getFirst()));
        db.a(); db.v2(); db.b();
        var permissions = List.copyOf(db.permissions); var grants = List.copyOf(db.grants); int writes = db.writes;
        db.a(); db.b();
        assertEquals(permissions, db.permissions); assertEquals(grants, db.grants); assertEquals(writes, db.writes);
        assertEquals(existing, withoutNulls(db.permissions.getFirst()));
    }

    @Test void metadataMismatchFailsBeforeAnyInsertAndDoesNotOverwrite() throws Exception {
        for (var entry : List.of(permission(73, "CATEGORY_READ", "Wrong", null), permission(73, "CATEGORY_READ", "CATEGORY_READ", ""))) {
            var db = new Jdbc(); db.permissions.add(entry);
            assertThrows(SQLException.class, db::a);
            assertEquals(List.of(entry), db.permissions); assertEquals(0, db.writes);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"ROLE_READ", "PERMISSION_READ", "CUSTOMER_DEACTIVATE", "ADJUSTMENT_REJECT",
            "ADJUSTMENT_CANCEL", "SALE_COMPLETE", "PAYMENT_VOID", "REFUND_READ", "REFUND_CREATE", "PRODUCT_READ", "UNKNOWN", "category_read"})
    void outOfScopeUnknownAndProvisionalCatalogRowsFailSafely(String code) throws Exception {
        var db = new Jdbc(); db.permissions.add(permission(73, code, code, null));
        assertThrows(SQLException.class, db::a); assertEquals(0, db.writes);
    }

    @Test void duplicatePermissionCodeOrIdIsRejected() throws Exception {
        for (boolean sameCode : List.of(true, false)) {
            var db = new Jdbc(); db.permissions.add(permission(73, "CATEGORY_READ", "CATEGORY_READ", null));
            db.permissions.add(permission(sameCode ? 74 : 73, sameCode ? "CATEGORY_READ" : "CATEGORY_CREATE",
                    sameCode ? "CATEGORY_READ" : "CATEGORY_CREATE", null));
            assertThrows(SQLException.class, db::a); assertEquals(0, db.writes);
        }
    }

    @Test void missingUnknownOrDuplicateRequiredRolesFailBeforeWriting() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            var db = new Jdbc();
            if (mode == 0) db.roles.removeIf(row -> row.get("code").equals("MANAGER"));
            if (mode == 1) db.roles.add(row("id", 999L, "code", "OTHER"));
            if (mode == 2) db.roles.add(row("id", 999L, "code", "MANAGER"));
            assertThrows(SQLException.class, db::a); assertEquals(0, db.writes);
        }
    }

    @Test void adminOrOrphanOrDuplicateMappingsAreConflictsNotAdoptedRows() throws Exception {
        for (int mode = 0; mode < 4; mode++) {
            var db = new Jdbc(); db.a(); db.v2(); db.writes = 0;
            var permissionId = (Long) db.permissions.getFirst().get("id");
            db.grants.add(grant(1, mode == 0 ? 41 : mode == 1 ? 777 : 78, mode == 2 ? 999 : permissionId));
            if (mode == 3) db.grants.add(grant(2, 78, permissionId));
            assertThrows(SQLException.class, db::b); assertEquals(0, db.writes);
        }
    }

    @Test void stageBRequiresSuccessfulV2AndAllFourCompatiblePermissions() throws Exception {
        var noV2 = new Jdbc(); assertThrows(SQLException.class, noV2::b); assertEquals(0, noV2.writes);
        var missing = new Jdbc(); missing.v2(); assertThrows(SQLException.class, missing::b); assertEquals(0, missing.writes);
    }

    @Test void targetMustBeLocalExactSchemaEvenIfJdbcCatalogWasSetElsewhere() throws Exception {
        for (var url : List.of("jdbc:mysql://127.0.0.1:3306/computer_store", "jdbc:mysql://remote:3306/computer_store_dev",
                "jdbc:mysql://127.0.0.1:3307/computer_store_dev", "jdbc:mysql://127.0.0.1/computer_store_dev?createDatabaseIfNotExist=true")) {
            var db = new Jdbc(); when(db.metadata.getURL()).thenReturn(url);
            assertThrows(SQLException.class, db::a); assertEquals(0, db.writes);
        }
        for (var schema : Arrays.asList(null, "computer_store")) {
            var db = new Jdbc(); db.schema = schema;
            assertThrows(SQLException.class, db::a); assertEquals(0, db.writes);
        }
    }

    @Test void noAutocommitReadOnlyOrUnsupportedIsolationCanPerformDml() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            var db = new Jdbc();
            if (mode == 0) when(db.connection.getAutoCommit()).thenReturn(true);
            if (mode == 1) when(db.connection.isReadOnly()).thenReturn(true);
            if (mode == 2) when(db.connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            assertThrows(SQLException.class, db::a); assertEquals(0, db.writes); db.noTransactionOwnership();
        }
    }

    @Test void missingOrNonTransactionalTablesAndFailedUnknownDuplicateOrWrongChecksumHistoryFailFast() throws Exception {
        for (int mode = 0; mode < 6; mode++) {
            var db = new Jdbc();
            if (mode == 0) db.engines.removeFirst();
            if (mode == 1) db.engines.getFirst().put("ENGINE", "MyISAM");
            if (mode == 2) db.history.getFirst().put("success", false);
            if (mode == 3) db.history.add(row("version", "4", "checksum", 1, "success", true));
            if (mode == 4) db.history.add(new HashMap<>(db.history.getFirst()));
            if (mode == 5) db.history.add(row("version", "2", "checksum", 1, "success", true));
            assertThrows(SQLException.class, db::a); assertEquals(0, db.writes);
        }
    }

    @Test void sqlFailurePropagatesToFlywayTransactionOwnerWithoutCommitRollbackRetryOrConnectionClose() throws Exception {
        var db = new Jdbc(); db.failAt = 2;
        assertSame(db.failure, assertThrows(SQLException.class, db::a));
        assertEquals(2, db.writes); assertEquals(1, db.permissions.size());
        // One staged insert exists in the JDBC stub: it must never be committed by migration code.
        // Physical rollback remains Flyway/MySQL's responsibility and needs an approved DB test.
        db.noTransactionOwnership();
    }

    @Test void zeroAffectedRowsIsFailureRatherThanFalseSuccess() throws Exception {
        var db = new Jdbc(); db.affected = 0;
        assertThrows(SQLException.class, db::a); assertEquals(1, db.writes); db.noTransactionOwnership();
    }

    @Test void stageBFailureNeverRewritesOrRemovesPreviouslyRegisteredStageARows() throws Exception {
        var db = new Jdbc(); db.a(); db.v2();
        var registered = List.copyOf(db.permissions);
        db.writes = 0; db.failAt = 3;
        assertSame(db.failure, assertThrows(SQLException.class, db::b));
        assertEquals(registered, db.permissions); assertEquals(2, db.grants.size());
        db.noTransactionOwnership();
    }

    @Test void postconditionDetectsUnexpectedUserRoleChangesAndFailsForRollback() throws Exception {
        var db = new Jdbc();
        db.afterWrite = () -> db.users.add(row("id", 999L, "user_id", 200L, "role_id", 41L, "assigned_at", OLD));
        assertThrows(SQLException.class, db::a); db.noTransactionOwnership();
    }

    @Test void versionsDescriptionsAndChecksumsArePinnedAndTransactional() {
        var a = new V2__register_category_permissions(); var b = new V3__grant_category_permissions();
        assertEquals("2", a.getVersion().toString()); assertEquals("3", b.getVersion().toString());
        assertEquals("register category permissions", a.getDescription());
        assertEquals("grant category permissions", b.getDescription());
        assertEquals(947719693, a.getChecksum()); assertEquals(253774843, b.getChecksum());
        assertEquals(a.getChecksum(), new V2__register_category_permissions().getChecksum());
        assertTrue(a.canExecuteInTransaction()); assertTrue(b.canExecuteInTransaction());
    }

    private static Map<String, Object> row(Object... values) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
    private static Map<String, Object> permission(long id, String code, String name, String description) {
        return row("id", id, "code", code, "name", name, "description", description, "created_at", OLD, "updated_at", OLD);
    }
    private static Map<String, Object> grant(long id, long role, long permission) {
        return row("id", id, "role_id", role, "permission_id", permission, "created_at", OLD);
    }
    private static Map<String, Object> withoutNulls(Map<String, Object> original) {
        var result = new HashMap<>(original); result.values().removeIf(Objects::isNull); return result;
    }

    static class Jdbc {
        final Connection connection = mock(Connection.class);
        final DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        final Context context = mock(Context.class);
        final List<Map<String, Object>> roles = new ArrayList<>(), permissions = new ArrayList<>(), grants = new ArrayList<>(), users = new ArrayList<>(), engines = new ArrayList<>(), history = new ArrayList<>();
        final List<String> prepared = new ArrayList<>();
        final SQLException failure = new SQLException("injected JDBC failure");
        int writes, failAt = -1, affected = 1; String schema = "computer_store_dev";
        Runnable afterWrite = () -> { };

        Jdbc() throws Exception {
            roles.addAll(List.of(row("id", 41L, "code", "ADMIN"), row("id", 78L, "code", "MANAGER"),
                    row("id", 93L, "code", "SALES_STAFF"), row("id", 105L, "code", "WAREHOUSE_STAFF")));
            users.add(row("id", 11L, "user_id", 7L, "role_id", 41L, "assigned_at", OLD));
            for (var table : List.of("app_role", "permission", "role_permission", "user_role", "flyway_schema_history")) engines.add(row("TABLE_NAME", table, "ENGINE", "InnoDB"));
            history.add(row("version", "1", "checksum", 123, "success", true));
            when(context.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.getURL()).thenReturn("jdbc:mysql://127.0.0.1:3306/computer_store_dev");
            when(metadata.getDatabaseProductName()).thenReturn("MySQL"); when(metadata.supportsTransactions()).thenReturn(true);
            when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_REPEATABLE_READ);
            when(connection.createStatement()).thenAnswer(call -> {
                var statement = mock(Statement.class);
                when(statement.executeQuery(anyString())).thenAnswer(query -> result(switch ((String) query.getArgument(0)) {
                    case FrozenCategoryRolloutV1.TARGET -> List.of(row("schema", schema));
                    case FrozenCategoryRolloutV1.ENGINES -> engines;
                    case FrozenCategoryRolloutV1.HISTORY -> history;
                    case FrozenCategoryRolloutV1.READ_ROLES -> roles;
                    case FrozenCategoryRolloutV1.READ_PERMISSIONS -> permissions;
                    case FrozenCategoryRolloutV1.READ_GRANTS -> grants;
                    case FrozenCategoryRolloutV1.READ_USERS -> users;
                    default -> throw new AssertionError("Unapproved read SQL");
                }));
                return statement;
            });
            when(connection.prepareStatement(anyString())).thenAnswer(call -> {
                String sql = call.getArgument(0); prepared.add(sql); var bound = new HashMap<Integer, Object>();
                var statement = mock(PreparedStatement.class);
                doAnswer(a -> { bound.put(a.getArgument(0), a.getArgument(1)); return null; }).when(statement).setString(anyInt(), anyString());
                doAnswer(a -> { bound.put(a.getArgument(0), a.getArgument(1)); return null; }).when(statement).setLong(anyInt(), anyLong());
                when(statement.executeUpdate()).thenAnswer(a -> {
                    if (++writes == failAt) throw failure;
                    if (affected == 0) return 0;
                    if (sql.equals(FrozenCategoryRolloutV1.INSERT_PERMISSION)) permissions.add(permission(1000 + permissions.size(), (String) bound.get(1), (String) bound.get(2), null));
                    else if (sql.equals(FrozenCategoryRolloutV1.INSERT_GRANT)) grants.add(grant(2000 + grants.size(), (Long) bound.get(1), (Long) bound.get(2)));
                    else throw new AssertionError("Only the two approved INSERTs may execute");
                    afterWrite.run(); return 1;
                });
                return statement;
            });
        }
        void a() throws Exception { new V2__register_category_permissions().migrate(context); }
        void b() throws Exception { new V3__grant_category_permissions().migrate(context); }
        void v2() { history.add(row("version", "2", "checksum", FrozenCategoryRolloutV1.checksum("2"), "success", true)); }
        Set<String> codes() { var codes = new HashSet<String>(); permissions.forEach(p -> codes.add((String) p.get("code"))); return codes; }
        Set<String> pairs() {
            var result = new HashSet<String>();
            for (var g : grants) result.add(roles.stream().filter(r -> r.get("id").equals(g.get("role_id"))).findFirst().orElseThrow().get("code")
                    + ":" + permissions.stream().filter(p -> p.get("id").equals(g.get("permission_id"))).findFirst().orElseThrow().get("code"));
            return result;
        }
        void noTransactionOwnership() throws Exception {
            verify(connection, never()).commit(); verify(connection, never()).rollback();
            verify(connection, never()).setAutoCommit(anyBoolean()); verify(connection, never()).close();
        }
        private static ResultSet result(List<Map<String, Object>> source) throws Exception {
            var values = new ArrayList<>(source); int[] index = {-1}; var rows = mock(ResultSet.class);
            when(rows.next()).thenAnswer(call -> ++index[0] < values.size());
            when(rows.getString(anyString())).thenAnswer(call -> (String) values.get(index[0]).get(call.getArgument(0)));
            when(rows.getString(1)).thenAnswer(call -> (String) values.get(index[0]).values().iterator().next());
            when(rows.getLong(anyString())).thenAnswer(call -> ((Number) values.get(index[0]).get(call.getArgument(0))).longValue());
            when(rows.getBoolean(anyString())).thenAnswer(call -> Boolean.TRUE.equals(values.get(index[0]).get(call.getArgument(0))));
            when(rows.getObject(anyString())).thenAnswer(call -> values.get(index[0]).get(call.getArgument(0)));
            when(rows.getObject(anyString(), eq(LocalDateTime.class))).thenAnswer(call -> values.get(index[0]).get(call.getArgument(0)));
            return rows;
        }
    }
}
