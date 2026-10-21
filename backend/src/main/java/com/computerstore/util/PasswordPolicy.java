package com.computerstore.util;

import com.computerstore.exception.AppException;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    /** Tối thiểu 8 ký tự, có ít nhất 1 chữ cái và 1 chữ số. */
    public static void ensureStrong(String password) {
        if (password == null || password.length() < 8
                || password.chars().noneMatch(Character::isLetter)
                || password.chars().noneMatch(Character::isDigit)) {
            throw new AppException(400, "Mật khẩu phải có ít nhất 8 ký tự, gồm cả chữ và số.");
        }
    }
}
