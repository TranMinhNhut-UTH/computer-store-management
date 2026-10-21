package com.computerstore.util;

import com.computerstore.exception.AppException;

import java.util.regex.Pattern;

public final class IdentifierUtil {
    private IdentifierUtil() {}

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^0\\d{9}$");

    public record Parsed(String value, boolean email) {}

    public static String normalizeEmail(String input) {
        String v = input.trim().toLowerCase();
        if (v.length() > 150 || !EMAIL.matcher(v).matches()) {
            throw new AppException(400, "Email không hợp lệ.");
        }
        return v;
    }

    /** Chuẩn hóa SĐT Việt Nam về dạng 0xxxxxxxxx (chấp nhận +84..., 84...). */
    public static String normalizePhone(String input) {
        String v = input.replaceAll("[\\s.\\-]", "");
        if (v.startsWith("+84")) {
            v = "0" + v.substring(3);
        } else if (v.startsWith("84") && v.length() == 11) {
            v = "0" + v.substring(2);
        }
        if (!PHONE.matcher(v).matches()) {
            throw new AppException(400, "Số điện thoại không hợp lệ.");
        }
        return v;
    }

    public static Parsed parse(String input) {
        return input.contains("@")
                ? new Parsed(normalizeEmail(input), true)
                : new Parsed(normalizePhone(input), false);
    }
}
