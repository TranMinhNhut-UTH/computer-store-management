package com.computerstore.computer_store_backend.identity.bootstrap;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import static com.computerstore.computer_store_backend.identity.bootstrap.AdminBootstrapException.Reason.*;

/** Deliberately small JDBC grammar: no multi-host, alternate catalog, credentials or arbitrary options. */
public final class BootstrapDatabaseTarget {
    private static final Pattern URL = Pattern.compile(
            "jdbc:mysql://(?:localhost|127\\.0\\.0\\.1|\\[::1\\])(?::([1-9][0-9]{0,4}))?/computer_store_dev(?:\\?([^#]+))?");
    private static final Set<String> SAFE_OPTIONS = Set.of(
            "serverTimezone=UTC", "connectionTimeZone=UTC", "forceConnectionTimeZoneToSession=true",
            "useSSL=true", "useSSL=false", "allowPublicKeyRetrieval=true", "allowPublicKeyRetrieval=false",
            "characterEncoding=UTF-8");
    private final String url;
    private final String username;
    private final String password;

    private BootstrapDatabaseTarget(String url, String username, String password) {
        this.url = url; this.username = username; this.password = password;
    }

    public static BootstrapDatabaseTarget fromEnvironment(Map<String, String> environment) {
        String url = environment.get("DB_URL");
        var match = URL.matcher(url == null ? "" : url);
        if (!match.matches() || (match.group(1) != null && Integer.parseInt(match.group(1)) > 65535)) {
            throw new AdminBootstrapException(UNSAFE_DATABASE_TARGET);
        }
        if (match.group(2) != null) {
            var keys = new java.util.HashSet<String>();
            for (String option : match.group(2).split("&", -1)) {
                if (!SAFE_OPTIONS.contains(option) || !keys.add(option.substring(0, option.indexOf('=')))) {
                    throw new AdminBootstrapException(UNSAFE_DATABASE_TARGET);
                }
            }
        }
        String username = environment.get("DB_USERNAME"), password = environment.get("DB_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new AdminBootstrapException(MISSING_DATABASE_CREDENTIALS);
        }
        return new BootstrapDatabaseTarget(url, username, password);
    }

    public String url() { return url; }
    public String username() { return username; }
    public String password() { return password; }
    @Override public String toString() { return "BootstrapDatabaseTarget[redacted]"; }
}
