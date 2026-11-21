package com.computerstore.computer_store_backend.shared.validation;

import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import java.util.Locale;

/** Pure normalization, no uniqueness checks. Null/empty remain distinct; request validation owns requiredness.
 * ASCII edge whitespace is SP and HT/LF/VT/FF/CR, not arbitrary control characters or Unicode spaces.
 */
public final class IdentifierNormalizer {
    private IdentifierNormalizer() { }

    public static String username(String value) { return identifier(value, false); }
    public static String businessCode(String value) { return identifier(value, true); }

    private static String identifier(String value, boolean uppercase) {
        String normalized = trimAscii(value);
        if (normalized == null || normalized.isEmpty()) return normalized;
        if (!normalized.matches("[A-Za-z0-9._-]+")) {
            throw invalid();
        }
        return uppercase ? normalized.toUpperCase(Locale.ROOT) : normalized.toLowerCase(Locale.ROOT);
    }

    /** Applies only app_user.email policy. Syntax validation (@Email) belongs to the request layer. */
    public static String userEmail(String value) {
        String normalized = trimAscii(value);
        if (normalized == null || normalized.isEmpty()) return normalized;
        if (normalized.chars().anyMatch(c -> c <= 32 || c >= 127)) throw invalid();
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid identifier format.");
    }

    private static String trimAscii(String value) {
        if (value == null) return null;
        int start = 0, end = value.length();
        while (start < end && whitespace(value.charAt(start))) start++;
        while (end > start && whitespace(value.charAt(end - 1))) end--;
        return value.substring(start, end);
    }

    private static boolean whitespace(char c) { return c == ' ' || (c >= '\t' && c <= '\r'); }
}
