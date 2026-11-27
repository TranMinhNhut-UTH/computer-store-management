package com.computerstore.computer_store_backend.inventory.service;

import com.computerstore.computer_store_backend.identity.authorization.PermissionPolicy;
import com.computerstore.computer_store_backend.inventory.dto.response.InventoryBalancePageResponse;
import com.computerstore.computer_store_backend.inventory.dto.response.InventoryBalanceResponse;
import com.computerstore.computer_store_backend.inventory.dto.response.InventoryMovementPageResponse;
import com.computerstore.computer_store_backend.inventory.dto.response.InventoryMovementResponse;
import com.computerstore.computer_store_backend.inventory.entity.Inventory;
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransaction;
import com.computerstore.computer_store_backend.inventory.entity.InventoryTransactionType;
import com.computerstore.computer_store_backend.inventory.repository.InventoryRepository;
import com.computerstore.computer_store_backend.inventory.repository.InventoryTransactionRepository;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import com.computerstore.computer_store_backend.shared.pagination.ListPagination;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.computerstore.computer_store_backend.identity.authorization.PermissionCode.INVENTORY_LEDGER_READ;
import static com.computerstore.computer_store_backend.identity.authorization.PermissionCode.INVENTORY_READ;

@Service
@Transactional(readOnly = true)
public class InventoryReadService {

    public enum BalanceFilter { ALL, LOW, OUT }

    private final PermissionPolicy permissions;
    private final InventoryRepository inventories;
    private final InventoryTransactionRepository movements;

    public InventoryReadService(PermissionPolicy permissions,
                                InventoryRepository inventories,
                                InventoryTransactionRepository movements) {
        this.permissions = permissions;
        this.inventories = inventories;
        this.movements = movements;
    }

    public InventoryBalancePageResponse balances(int page, int size, BalanceFilter filter) {
        permissions.requirePermission(INVENTORY_READ.code());
        if (filter == null) filter = BalanceFilter.ALL;
        var pagination = ListPagination.request(page, size, "product.sku",
                () -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid inventory pagination."));
        var result = switch (filter) {
            case ALL -> inventories.findActiveBalances(pagination);
            case LOW -> inventories.findActiveLowStockBalances(pagination);
            case OUT -> inventories.findActiveOutOfStockBalances(pagination);
        };
        return new InventoryBalancePageResponse(result.getContent().stream().map(InventoryReadService::balance).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public InventoryMovementPageResponse movements(int page, int size, String productId,
                                                    InventoryTransactionType type,
                                                    LocalDateTime from, LocalDateTime to) {
        permissions.requirePermission(INVENTORY_LEDGER_READ.code());
        if (to != null && from != null && to.isBefore(from)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid inventory movement time range.");
        }
        if (to != null && from != null && to.equals(from)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid inventory movement time range.");
        }
        Long id = productId == null ? null : parseId(productId);
        var pagination = ListPagination.request(page, size, "occurredAt",
                () -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid inventory pagination."));
        var result = movements.search(id, type, from, to, pagination);
        return new InventoryMovementPageResponse(result.getContent().stream()
                .map(InventoryReadService::movement).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private static InventoryBalanceResponse balance(Inventory inventory) {
        long quantity = inventory.getQuantityOnHand();
        long threshold = inventory.getProduct().getMinStockLevel();
        var status = quantity == 0
                ? InventoryBalanceResponse.StockStatus.OUT_OF_STOCK
                : quantity <= threshold
                    ? InventoryBalanceResponse.StockStatus.LOW_STOCK
                    : InventoryBalanceResponse.StockStatus.NORMAL;
        return new InventoryBalanceResponse(inventory.getProduct().getId().toString(),
                inventory.getProduct().getSku(), inventory.getProduct().getName(),
                Long.toString(quantity), Long.toString(threshold), status);
    }

    private static InventoryMovementResponse movement(InventoryTransaction transaction) {
        String sourceType = null;
        String sourceId = null;
        if (transaction.getPurchaseItem() != null) {
            sourceType = "PURCHASE_ITEM"; sourceId = transaction.getPurchaseItem().getId().toString();
        } else if (transaction.getSalesOrderItem() != null) {
            sourceType = "SALES_ORDER_ITEM"; sourceId = transaction.getSalesOrderItem().getId().toString();
        } else if (transaction.getSalesReturnItem() != null) {
            sourceType = "SALES_RETURN_ITEM"; sourceId = transaction.getSalesReturnItem().getId().toString();
        } else if (transaction.getAdjustmentItem() != null) {
            sourceType = "INVENTORY_ADJUSTMENT_ITEM"; sourceId = transaction.getAdjustmentItem().getId().toString();
        } else if (transaction.getReversalOfTransaction() != null) {
            sourceType = "REVERSAL"; sourceId = transaction.getReversalOfTransaction().getId().toString();
        }
        return new InventoryMovementResponse(
                transaction.getId().toString(),
                transaction.getProduct().getId().toString(),
                transaction.getProduct().getSku(),
                transaction.getProduct().getName(),
                transaction.getTransactionType(),
                Long.toString(transaction.getQuantityDelta()),
                Long.toString(transaction.getQuantityAfter()),
                transaction.getOccurredAt().toInstant(ZoneOffset.UTC),
                transaction.getRecordedBy().getUsername(),
                sourceType,
                sourceId,
                transaction.getReversalOfTransaction() == null ? null : transaction.getReversalOfTransaction().getId().toString(),
                transaction.getReversalReason(),
                transaction.getNote(),
                transaction.getCreatedAt().toInstant(ZoneOffset.UTC));
    }

    private static Long parseId(String value) {
        if (value == null || !value.matches("0|[1-9][0-9]{0,18}") || value.equals("0")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid inventory product id.");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid inventory product id.");
        }
    }
}
