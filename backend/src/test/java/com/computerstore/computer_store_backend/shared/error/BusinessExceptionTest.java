package com.computerstore.computer_store_backend.shared.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;

class BusinessExceptionTest {
    @Test void codesHaveStableMappings() {
        assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.status());
        assertEquals(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.status());
        assertEquals(HttpStatus.CONFLICT, ErrorCode.BUSINESS_CONFLICT.status());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.status());
    }
    @Test void businessExceptionCarriesOnlyPublicContract() {
        BusinessException exception = new BusinessException(ErrorCode.BUSINESS_CONFLICT, "Operation unavailable.");
        assertEquals(ErrorCode.BUSINESS_CONFLICT, exception.code());
        assertEquals("Operation unavailable.", exception.publicDetail());
        assertInstanceOf(RuntimeException.class, exception);
    }
    @Test void invalidConstructionIsRejected() {
        assertThrows(NullPointerException.class, () -> new BusinessException(null, "Safe."));
        assertThrows(NullPointerException.class, () -> new BusinessException(ErrorCode.BUSINESS_CONFLICT, null));
        assertThrows(IllegalArgumentException.class, () -> new BusinessException(ErrorCode.BUSINESS_CONFLICT, " "));
        assertThrows(IllegalArgumentException.class, () -> new BusinessException(ErrorCode.INTERNAL_ERROR, "secret"));
    }
}
