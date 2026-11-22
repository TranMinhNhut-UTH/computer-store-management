package com.computerstore.computer_store_backend.inventory.exception;

import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;

import java.util.List;
import java.util.stream.Collectors;

/** Factory tạo BusinessException cho inventory — không tạo exception subclass vì
 * BusinessException (shared/error) là final. */
public final class InventoryErrors {
    private InventoryErrors() { }

    public static BusinessException inventoryRowNotFound(Long productId) {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "Inventory record was not found for the requested product.");
    }

    public static BusinessException skuLockedByStocktake(Long productId) {
        return new BusinessException(ErrorCode.BUSINESS_CONFLICT,
                "This product is currently locked by an open stocktake or inventory adjustment.");
    }

    public static BusinessException insufficientStock(List<ShortfallLine> shortfalls) {
        String detail = shortfalls.stream()
                .map(s -> "product " + s.productId() + ": requested " + s.requested() + ", available " + s.available())
                .collect(Collectors.joining("; "));
        return new BusinessException(ErrorCode.BUSINESS_CONFLICT,
                "Insufficient stock for one or more items (" + detail + ").");
    }
}