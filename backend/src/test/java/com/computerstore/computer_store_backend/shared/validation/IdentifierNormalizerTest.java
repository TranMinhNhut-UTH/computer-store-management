package com.computerstore.computer_store_backend.shared.validation;

import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentifierNormalizerTest {
    @Test void normalizesAsciiIdentifiers() {
        assertEquals("user.name-01_x", IdentifierNormalizer.username(" \tUSER.Name-01_X\r\n"));
        assertEquals("SKU-01._X", IdentifierNormalizer.businessCode(" sku-01._x "));
        assertEquals("user@example.com", IdentifierNormalizer.userEmail(" USER@EXAMPLE.COM "));
    }
    @Test void preservesNullAndEmptyWithoutInventingRequirednessOrBlankEmailPolicy() {
        assertNull(IdentifierNormalizer.username(null));
        assertNull(IdentifierNormalizer.businessCode(null));
        assertNull(IdentifierNormalizer.userEmail(null));
        assertEquals("", IdentifierNormalizer.username(" \t\n\u000b\f\r"));
        assertEquals("", IdentifierNormalizer.businessCode(""));
        assertEquals("", IdentifierNormalizer.userEmail(" \t "));
    }
    @Test void rejectsUnicodeInternalWhitespaceAndInvalidCharactersBeforeCaseConversion() {
        for (String input : new String[]{"tên", "a b", "a\tb", "A/B", "a@b", "\u00a0ABC", "\u0000ABC", "ı", "ſ"}) {
            assertThrows(BusinessException.class, () -> IdentifierNormalizer.username(input));
            assertThrows(BusinessException.class, () -> IdentifierNormalizer.businessCode(input));
        }
    }
    @Test void emailDoesNotAcceptUnicodeOrWhitespace() {
        for (String input : new String[]{"a b@example.com", "a@tên.vn", "a@b.com\u00a0", "a\u007f@b.com"}) {
            BusinessException exception = assertThrows(BusinessException.class, () -> IdentifierNormalizer.userEmail(input));
            assertEquals(ErrorCode.VALIDATION_ERROR, exception.code());
            assertFalse(exception.publicDetail().contains(input));
        }
    }
    @Test void normalizationIsIdempotent() {
        String code = IdentifierNormalizer.businessCode(" sku-1 ");
        assertEquals(code, IdentifierNormalizer.businessCode(code));
        String username = IdentifierNormalizer.username(" USER ");
        assertEquals(username, IdentifierNormalizer.username(username));
    }
}
