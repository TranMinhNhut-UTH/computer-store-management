package com.computerstore.computer_store_backend.inventory.controller;

import com.computerstore.computer_store_backend.inventory.dto.response.InventoryBalancePageResponse;
import com.computerstore.computer_store_backend.inventory.dto.response.InventoryMovementPageResponse;
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransactionType;
import com.computerstore.computer_store_backend.inventory.service.InventoryReadService;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryReadController {
    private final InventoryReadService inventory;

    public InventoryReadController(InventoryReadService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/balances")
    public InventoryBalancePageResponse balances(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ALL") InventoryReadService.BalanceFilter filter) {
        return inventory.balances(page, size, filter);
    }

    @GetMapping("/movements")
    public InventoryMovementPageResponse movements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) InventoryTransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return inventory.movements(page, size, productId, type, from, to);
    }
}
