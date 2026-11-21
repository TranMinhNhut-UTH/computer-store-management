package com.computerstore.computer_store_backend.identity.authorization.registration;

import com.computerstore.computer_store_backend.identity.authorization.PermissionCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

/**
 * Pure Stage A preparation value: validates caller-supplied proposed entries and fingerprints their content.
 * It neither approves a manifest nor registers permissions, grants roles or creates accounts.
 * No default manifest, Spring component, Flyway migration or database dependency is provided.
 */
public final class PermissionCatalogManifest {
    private static final String FINGERPRINT_FORMAT = "CSM_PERMISSION_CATALOG_MANIFEST_V1";

    public record Entry(String code, String name, String description) { }

    private final List<Entry> entries;
    private final String contentSha256;

    private PermissionCatalogManifest(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        this.contentSha256 = fingerprint(this.entries);
    }

    /** Reject invalid data without normalization; successful validation is not rollout approval. */
    public static PermissionCatalogManifest validate(List<Entry> proposedEntries) {
        if (proposedEntries == null || proposedEntries.isEmpty()) {
            throw new IllegalArgumentException("Permission catalog manifest must not be empty.");
        }
        var snapshot = new ArrayList<Entry>();
        var codes = new HashSet<String>();
        for (var entry : proposedEntries) {
            if (entry == null) throw new IllegalArgumentException("Manifest entries must not be null.");
            var code = entry.code();
            if (code == null || !code.matches("[A-Z0-9_]{1,100}")) {
                throw new IllegalArgumentException("Permission code must be canonical and at most 100 characters.");
            }
            var permission = PermissionCode.fromCode(code).orElseThrow(
                    () -> new IllegalArgumentException("Permission code is not in the current catalog."));
            if (permission.status() != PermissionCode.Status.ELIGIBLE_PROPOSED) {
                throw new IllegalArgumentException("Provisional permissions cannot be registered.");
            }
            if (!codes.add(code)) throw new IllegalArgumentException("Manifest contains duplicate permission codes.");
            validateText(entry.name(), 120, true);
            validateText(entry.description(), 255, false);
            snapshot.add(entry);
        }
        snapshot.sort(Comparator.comparing(Entry::code));
        return new PermissionCatalogManifest(snapshot);
    }

    public List<Entry> entries() { return entries; }

    /** Content identity only: not an approval signature or a Flyway migration checksum. */
    public String contentSha256() { return contentSha256; }

    private static void validateText(String value, int maxCharacters, boolean required) {
        if (value == null) {
            if (required) throw new IllegalArgumentException("Permission name must not be null.");
            return;
        }
        if ((required && value.isBlank()) || value.codePointCount(0, value.length()) > maxCharacters
                || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) {
            throw new IllegalArgumentException(required
                    ? "Permission name must be nonblank valid Unicode and at most 120 characters."
                    : "Permission description must be valid Unicode and at most 255 characters.");
        }
    }

    private static String fingerprint(List<Entry> entries) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            updateText(digest, FINGERPRINT_FORMAT);
            updateInt(digest, entries.size());
            for (var entry : entries) {
                updateText(digest, entry.code());
                updateText(digest, entry.name());
                updateText(digest, entry.description());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable.", unavailable);
        }
    }

    private static void updateText(MessageDigest digest, String value) {
        if (value == null) {
            updateInt(digest, -1);
            return;
        }
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }
}
