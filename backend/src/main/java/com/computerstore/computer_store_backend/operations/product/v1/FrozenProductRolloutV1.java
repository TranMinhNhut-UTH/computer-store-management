package com.computerstore.computer_store_backend.operations.product.v1;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CRC32;

/** Frozen Product V4/V5 contract. Changes after application require another migration version. */
final class FrozenProductRolloutV1 {
    private FrozenProductRolloutV1() { }

    static final String SCHEMA = "computer_store_dev";
    static final String URL = "jdbc:mysql://127.0.0.1:3306/computer_store_dev";
    static final List<String> CODES = List.of("PRODUCT_CREATE", "PRODUCT_DEACTIVATE",
            "PRODUCT_PRICE_UPDATE", "PRODUCT_READ", "PRODUCT_UPDATE");
    static final List<String> PAIRS = List.of("MANAGER:PRODUCT_CREATE", "MANAGER:PRODUCT_DEACTIVATE",
            "MANAGER:PRODUCT_PRICE_UPDATE", "MANAGER:PRODUCT_READ", "MANAGER:PRODUCT_UPDATE",
            "SALES_STAFF:PRODUCT_READ", "WAREHOUSE_STAFF:PRODUCT_READ");
    private static final List<String> ROLES = List.of("ADMIN", "MANAGER", "SALES_STAFF", "WAREHOUSE_STAFF");
    private static final List<String> DESCRIPTIONS = List.of("create initial schema", "register category permissions",
            "grant category permissions", "register product permissions", "grant product permissions");
    private static final List<Integer> BASE_CHECKSUMS = List.of(-1142478933, 947719693, 253774843);
    private static final Set<String> TABLES = Set.of("app_role", "permission", "role_permission", "flyway_schema_history");
    static final String TARGET = "SELECT DATABASE()";
    static final String ENGINES = "SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES"
            + " WHERE TABLE_SCHEMA = 'computer_store_dev'"
            + " AND TABLE_NAME IN ('app_role','permission','role_permission','flyway_schema_history')";
    static final String HISTORY = "SELECT version, description, type, checksum, success"
            + " FROM computer_store_dev.flyway_schema_history ORDER BY installed_rank";
    static final String READ_ROLES = "SELECT id, code FROM computer_store_dev.app_role ORDER BY id";
    static final String READ_PERMISSIONS = "SELECT id, code, name, description, created_at, updated_at"
            + " FROM computer_store_dev.permission ORDER BY id";
    static final String READ_GRANTS = "SELECT id, role_id, permission_id, created_at"
            + " FROM computer_store_dev.role_permission ORDER BY id";
    static final String LOCK = " FOR UPDATE";
    static final String INSERT_PERMISSION = "INSERT INTO computer_store_dev.permission"
            + " (code,name,description,created_at,updated_at) VALUES (?,?,NULL,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))";
    static final String INSERT_GRANT = "INSERT INTO computer_store_dev.role_permission"
            + " (role_id,permission_id,created_at) VALUES (?,?,UTC_TIMESTAMP(6))";

    static void verifyUrl(String url) throws SQLException {
        check(URL.equals(url), "unexpected JDBC target");
    }

    private static int verifyTargetAndHistory(Connection connection, int version) throws SQLException {
        var metadata = connection.getMetaData();
        verifyUrl(metadata.getURL());
        check("MySQL".equals(metadata.getDatabaseProductName()) && metadata.supportsTransactions(), "MySQL transactions required");
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(TARGET)) {
            check(rows.next() && SCHEMA.equals(rows.getString(1)) && !rows.next(), "unexpected connected schema");
        }
        var tables = new HashSet<String>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(ENGINES)) {
            while (rows.next()) {
                check("InnoDB".equalsIgnoreCase(rows.getString("ENGINE")) && tables.add(rows.getString("TABLE_NAME")),
                        "InnoDB tables required");
            }
        }
        check(tables.equals(TABLES), "required tables missing");
        int applied = 0;
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(HISTORY)) {
            while (rows.next()) {
                int expected = ++applied;
                check(expected <= version
                        && Integer.toString(expected).equals(rows.getString("version"))
                        && DESCRIPTIONS.get(expected - 1).equals(rows.getString("description"))
                        && (expected == 1 ? "SQL" : "JDBC").equals(rows.getString("type"))
                        && Objects.equals(expectedChecksum(expected), rows.getObject("checksum"))
                        && rows.getBoolean("success"), "unexpected Flyway version, checksum or state");
            }
        }
        check(applied == version - 1 || applied == version, "required Flyway predecessors are missing");
        return applied;
    }

    static void verifyState(Connection connection, int version, boolean requireApplied) throws SQLException {
        int applied = verifyTargetAndHistory(connection, version);
        check(!requireApplied || applied == version, "selected migration was not applied");
        var state = snapshot(connection, "");
        requireComplete(state, applied);
    }

    record Permission(long id, String name, String description, LocalDateTime created, LocalDateTime updated) { }
    record Grant(long id, LocalDateTime created) { }
    record Pair(String role, String code) { }
    record Snapshot(Map<String, Long> roles, Map<String, Permission> permissions, Map<Pair, Grant> grants) { }

    private static Snapshot snapshot(Connection connection, String lock) throws SQLException {
        var roles = new HashMap<String, Long>();
        var roleCodes = new HashMap<Long, String>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(READ_ROLES + lock)) {
            while (rows.next()) {
                long id = rows.getLong("id");
                String code = rows.getString("code");
                check(id > 0 && code != null && ROLES.contains(code)
                        && roles.putIfAbsent(code, id) == null && roleCodes.putIfAbsent(id, code) == null,
                        "unknown or duplicate role");
            }
        }
        check(roles.keySet().equals(Set.copyOf(ROLES)), "required V1 roles missing");

        var permissions = new HashMap<String, Permission>();
        var permissionCodes = new HashMap<Long, String>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(READ_PERMISSIONS + lock)) {
            while (rows.next()) {
                String code = rows.getString("code");
                var row = new Permission(rows.getLong("id"), rows.getString("name"), rows.getString("description"),
                        rows.getObject("created_at", LocalDateTime.class), rows.getObject("updated_at", LocalDateTime.class));
                check(code != null && row.id() > 0 && row.created() != null && row.updated() != null
                        && permissions.putIfAbsent(code, row) == null && permissionCodes.putIfAbsent(row.id(), code) == null,
                        "invalid or duplicate permission row");
                if (productCode(code)) {
                    check(CODES.contains(code) && code.equals(row.name()) && row.description() == null,
                            "unknown Product permission or conflicting metadata");
                }
            }
        }

        var grants = new HashMap<Pair, Grant>();
        var grantIds = new HashSet<Long>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(READ_GRANTS + lock)) {
            while (rows.next()) {
                var pair = new Pair(roleCodes.get(rows.getLong("role_id")), permissionCodes.get(rows.getLong("permission_id")));
                var row = new Grant(rows.getLong("id"), rows.getObject("created_at", LocalDateTime.class));
                check(pair.role() != null && pair.code() != null && row.id() > 0 && row.created() != null
                        && grantIds.add(row.id()) && grants.putIfAbsent(pair, row) == null, "invalid or duplicate role mapping");
                if (productCode(pair.code())) {
                    check(PAIRS.contains(pair.role() + ":" + pair.code()), "unapproved Product role mapping");
                }
            }
        }
        return new Snapshot(Map.copyOf(roles), Map.copyOf(permissions), Map.copyOf(grants));
    }

    static void migrate(Connection connection, int version) throws SQLException {
        check(!connection.getAutoCommit() && !connection.isReadOnly(), "writable transaction required");
        int isolation = connection.getTransactionIsolation();
        check(isolation == Connection.TRANSACTION_REPEATABLE_READ || isolation == Connection.TRANSACTION_SERIALIZABLE,
                "unsupported transaction isolation");
        check(verifyTargetAndHistory(connection, version) == version - 1, "selected migration is already applied");
        var before = snapshot(connection, LOCK);
        requireComplete(before, version - 1);
        if (version == 4) {
            for (String code : CODES) {
                if (!before.permissions().containsKey(code)) insertPermission(connection, code);
            }
        } else {
            for (String code : PAIRS) {
                Pair pair = pair(code);
                if (!before.grants().containsKey(pair)) insertGrant(connection, before, pair);
            }
        }
        var after = snapshot(connection, LOCK);
        requireComplete(after, version);
        verifyChanges(before, after, version);
        // Any exception is returned to the Flyway transaction owner.
    }

    private static void insertPermission(Connection connection, String code) throws SQLException {
        try (var insert = connection.prepareStatement(INSERT_PERMISSION)) {
            insert.setString(1, code);
            insert.setString(2, code);
            check(insert.executeUpdate() == 1, "permission insert did not affect one row");
        }
    }

    private static void insertGrant(Connection connection, Snapshot before, Pair pair) throws SQLException {
        try (var insert = connection.prepareStatement(INSERT_GRANT)) {
            insert.setLong(1, before.roles().get(pair.role()));
            insert.setLong(2, before.permissions().get(pair.code()).id());
            check(insert.executeUpdate() == 1, "role mapping insert did not affect one row");
        }
    }

    private static void verifyChanges(Snapshot before, Snapshot after, int version) throws SQLException {
        check(before.roles().equals(after.roles())
                && after.permissions().entrySet().containsAll(before.permissions().entrySet())
                && after.grants().entrySet().containsAll(before.grants().entrySet()), "existing data changed");
        var expectedPermissions = new HashSet<>(before.permissions().keySet());
        var expectedGrants = new HashSet<>(before.grants().keySet());
        if (version == 4) expectedPermissions.addAll(CODES);
        else PAIRS.stream().map(FrozenProductRolloutV1::pair).forEach(expectedGrants::add);
        check(after.permissions().keySet().equals(expectedPermissions) && after.grants().keySet().equals(expectedGrants),
                "unexpected write set");
    }

    private static void requireComplete(Snapshot state, int version) throws SQLException {
        if (version >= 4) check(state.permissions().keySet().containsAll(CODES), "Product catalog incomplete");
        if (version >= 5) {
            check(PAIRS.stream().map(FrozenProductRolloutV1::pair).allMatch(state.grants()::containsKey),
                    "Product role mappings incomplete");
        }
    }

    private static boolean productCode(String code) { return code.regionMatches(true, 0, "PRODUCT_", 0, 8); }

    private static Pair pair(String code) {
        int separator = code.indexOf(':');
        return new Pair(code.substring(0, separator), code.substring(separator + 1));
    }

    private static int expectedChecksum(int version) {
        return version <= 3 ? BASE_CHECKSUMS.get(version - 1) : checksum(version);
    }

    static int checksum(int version) {
        var body = String.join("\n", "CSM_PRODUCT_ROLLOUT_V1_VALIDATION_R1", Integer.toString(version), URL,
                String.join(",", CODES), "name=code;description=NULL", String.join(",", PAIRS), String.join(",", ROLES),
                String.join(",", DESCRIPTIONS), BASE_CHECKSUMS.toString(), TARGET, ENGINES, HISTORY,
                READ_ROLES, READ_PERMISSIONS, READ_GRANTS, LOCK, INSERT_PERMISSION, INSERT_GRANT);
        var crc = new CRC32();
        crc.update(body.getBytes(StandardCharsets.UTF_8));
        return (int) crc.getValue();
    }

    private static void check(boolean valid, String reason) throws SQLException {
        if (!valid) throw new SQLException("Product rollout stopped: " + reason + ".");
    }
}
