package com.computerstore.computer_store_backend.inventory.exception;

public record ShortfallLine(Long productId, long requested, long available) {
}