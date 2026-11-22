package com.computerstore.computer_store_backend.operations.category.v1;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/** Frozen V2/V3 implementation. Never replace its payload with the live permission catalog. */
final class FrozenCategoryRolloutV1 {
    private FrozenCategoryRolloutV1() { }
    static final String SCHEMA = "computer_store_dev";
    static final List<String> CODES = List.of("CATEGORY_CREATE", "CATEGORY_DEACTIVATE", "CATEGORY_READ", "CATEGORY_UPDATE");
    static final Set<String> ROLES = Set.of("ADMIN", "MANAGER", "SALES_STAFF", "WAREHOUSE_STAFF");
    static final List<String> PAIRS = List.of("MANAGER:CATEGORY_CREATE", "MANAGER:CATEGORY_DEACTIVATE",
            "MANAGER:CATEGORY_READ", "MANAGER:CATEGORY_UPDATE", "SALES_STAFF:CATEGORY_READ", "WAREHOUSE_STAFF:CATEGORY_READ");
    static final String TARGET = "SELECT DATABASE()";
    static final String ENGINES = "SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'computer_store_dev'"
            + " AND TABLE_NAME IN ('app_role','permission','role_permission','user_role','flyway_schema_history')";
    static final String HISTORY = "SELECT version, checksum, success FROM computer_store_dev.flyway_schema_history ORDER BY installed_rank";
    static final String READ_ROLES = "SELECT id, code FROM computer_store_dev.app_role ORDER BY id FOR UPDATE";
    static final String READ_PERMISSIONS = "SELECT id, code, name, description, created_at, updated_at FROM computer_store_dev.permission ORDER BY id FOR UPDATE";
    static final String READ_GRANTS = "SELECT id, role_id, permission_id, created_at FROM computer_store_dev.role_permission ORDER BY id FOR UPDATE";
    static final String READ_USERS = "SELECT id, user_id, role_id, assigned_at FROM computer_store_dev.user_role ORDER BY id FOR UPDATE";
    static final String INSERT_PERMISSION = "INSERT INTO computer_store_dev.permission (code,name,description,created_at,updated_at)"
            + " VALUES (?,?,NULL,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))";
    static final String INSERT_GRANT = "INSERT INTO computer_store_dev.role_permission (role_id,permission_id,created_at) VALUES (?,?,UTC_TIMESTAMP(6))";
    private static final Pattern URL = Pattern.compile("jdbc:mysql://127\\.0\\.0\\.1(?::3306)?/computer_store_dev(?:\\?([^#]+))?");
    private static final Set<String> OPTIONS = Set.of("serverTimezone=UTC", "connectionTimeZone=UTC",
            "forceConnectionTimeZoneToSession=true", "useSSL=true", "useSSL=false", "allowPublicKeyRetrieval=true",
            "allowPublicKeyRetrieval=false", "characterEncoding=UTF-8");

    static void verifyUrl(String url) throws SQLException {
        var match = URL.matcher(url == null ? "" : url);
        check(match.matches());
        if (match.group(1) != null) {
            var keys = new HashSet<String>();
            for (var option : match.group(1).split("&", -1)) {
                check(OPTIONS.contains(option) && keys.add(option.substring(0, option.indexOf('='))));
            }
        }
    }

    static void verifyTargetAndHistory(Connection connection, boolean stageB) throws SQLException {
        var metadata = connection.getMetaData();
        verifyUrl(metadata.getURL());
        check("MySQL".equals(metadata.getDatabaseProductName()) && metadata.supportsTransactions());
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(TARGET)) {
            check(rows.next() && SCHEMA.equals(rows.getString(1)) && !rows.next());
        }
        var tables = new HashSet<String>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(ENGINES)) {
            while (rows.next()) check("InnoDB".equalsIgnoreCase(rows.getString("ENGINE")) && tables.add(rows.getString("TABLE_NAME")));
        }
        check(tables.equals(Set.of("app_role", "permission", "role_permission", "user_role", "flyway_schema_history")));
        var versions = new HashSet<String>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(HISTORY)) {
            while (rows.next()) {
                var version = rows.getString("version");
                check(version != null && Set.of("1", "2", "3").contains(version) && versions.add(version) && rows.getBoolean("success"));
                if (!version.equals("1")) check(Objects.equals(rows.getObject("checksum"), checksum(version)));
            }
        }
        check(versions.contains("1") && (!stageB || versions.contains("2")));
    }

    record Permission(long id, String code, String name, String description, LocalDateTime created, LocalDateTime updated) { }
    record Grant(long id, long role, long permission, LocalDateTime created) { }
    record UserLink(long id, long user, long role, LocalDateTime assigned) { }
    record Snapshot(Map<String, Long> roles, Map<String, Permission> permissions, Map<String, Grant> grants, List<UserLink> users) { }

    static Snapshot snapshot(Connection connection) throws SQLException {
        var roles = new TreeMap<String, Long>();
        var ids = new HashSet<Long>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(READ_ROLES)) {
            while (rows.next()) {
                long id = rows.getLong("id"); var code = rows.getString("code");
                check(id > 0 && code != null && ROLES.contains(code) && ids.add(id) && roles.putIfAbsent(code, id) == null);
            }
        }
        check(roles.keySet().equals(ROLES));
        var permissions = new TreeMap<String, Permission>(); ids.clear();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(READ_PERMISSIONS)) {
            while (rows.next()) {
                long id = rows.getLong("id"); var code = rows.getString("code");
                check(id > 0 && code != null && CODES.contains(code) && ids.add(id));
                var value = new Permission(id, code, rows.getString("name"), rows.getString("description"),
                        rows.getObject("created_at", LocalDateTime.class), rows.getObject("updated_at", LocalDateTime.class));
                check(code.equals(value.name()) && value.description() == null && value.created() != null && value.updated() != null);
                check(permissions.putIfAbsent(code, value) == null);
            }
        }
        var grants = new TreeMap<String, Grant>(); ids.clear();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(READ_GRANTS)) {
            while (rows.next()) {
                var value = new Grant(rows.getLong("id"), rows.getLong("role_id"), rows.getLong("permission_id"),
                        rows.getObject("created_at", LocalDateTime.class));
                var role = roles.entrySet().stream().filter(e -> e.getValue() == value.role()).map(Map.Entry::getKey).findFirst();
                var permission = permissions.values().stream().filter(p -> p.id() == value.permission()).map(Permission::code).findFirst();
                check(value.id() > 0 && value.created() != null && ids.add(value.id()) && role.isPresent() && permission.isPresent());
                var pair = role.get() + ":" + permission.get();
                check(PAIRS.contains(pair) && grants.putIfAbsent(pair, value) == null);
            }
        }
        var users = new ArrayList<UserLink>(); ids.clear(); var pairs = new HashSet<String>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(READ_USERS)) {
            while (rows.next()) {
                var value = new UserLink(rows.getLong("id"), rows.getLong("user_id"), rows.getLong("role_id"),
                        rows.getObject("assigned_at", LocalDateTime.class));
                check(value.id() > 0 && value.user() > 0 && roles.containsValue(value.role()) && value.assigned() != null
                        && ids.add(value.id()) && pairs.add(value.user() + ":" + value.role()));
                users.add(value);
            }
        }
        return new Snapshot(Map.copyOf(roles), Map.copyOf(permissions), Map.copyOf(grants), List.copyOf(users));
    }

    static void migrate(Connection connection, boolean stageB) throws SQLException {
        // Flyway owns commit/rollback. Refuse direct autocommit or read-only execution.
        check(!connection.getAutoCommit() && !connection.isReadOnly());
        check(connection.getTransactionIsolation() == Connection.TRANSACTION_REPEATABLE_READ
                || connection.getTransactionIsolation() == Connection.TRANSACTION_SERIALIZABLE);
        verifyTargetAndHistory(connection, stageB);
        var before = snapshot(connection);
        if (stageB) check(before.permissions().keySet().equals(Set.copyOf(CODES)));
        if (!stageB) {
            for (var code : CODES) if (!before.permissions().containsKey(code)) {
                try (var insert = connection.prepareStatement(INSERT_PERMISSION)) {
                    insert.setString(1, code); insert.setString(2, code);
                    check(insert.executeUpdate() == 1);
                }
            }
        } else {
            for (var pair : PAIRS) if (!before.grants().containsKey(pair)) {
                var parts = pair.split(":");
                try (var insert = connection.prepareStatement(INSERT_GRANT)) {
                    insert.setLong(1, before.roles().get(parts[0]));
                    insert.setLong(2, before.permissions().get(parts[1]).id());
                    check(insert.executeUpdate() == 1);
                }
            }
        }
        var after = snapshot(connection);
        check(before.roles().equals(after.roles()) && before.users().equals(after.users()));
        check(after.permissions().entrySet().containsAll(before.permissions().entrySet())
                && after.grants().entrySet().containsAll(before.grants().entrySet()));
        check(after.permissions().keySet().equals(Set.copyOf(CODES)));
        if (stageB) check(before.permissions().equals(after.permissions()) && after.grants().keySet().equals(Set.copyOf(PAIRS)));
        else check(before.grants().equals(after.grants()));
    }

    static int checksum(String version) {
        // Frozen SQL + payload + validation revision; changing applied implementation requires a NEW version.
        var content = String.join("\n", "CSM_CATEGORY_ROLLOUT_V1_VALIDATION_R1", version, String.join(",", CODES),
                "name=code;description=NULL", String.join(",", PAIRS), String.join(",", new TreeSet<>(ROLES)),
                String.join(",", new TreeSet<>(OPTIONS)), TARGET, ENGINES, HISTORY, READ_ROLES,
                READ_PERMISSIONS, READ_GRANTS, READ_USERS, INSERT_PERMISSION, INSERT_GRANT);
        var crc = new CRC32(); crc.update(content.getBytes(StandardCharsets.UTF_8));
        return (int) crc.getValue();
    }

    private static void check(boolean valid) throws SQLException {
        if (!valid) throw new SQLException("Category rollout target, history or integrity check failed; stop and review.");
    }
}
