package com.computerstore.computer_store_backend.identity.authorization;

import java.util.Optional;

/**
 * Proposed V1 catalog, not role grants, seed data or approval of an API policy.
 * ELIGIBLE_PROPOSED only permits technical preparation; actual server mapping is still required.
 */
public enum PermissionCode {
    // Identity / Employee
    EMPLOYEE_READ(Module.IDENTITY_EMPLOYEE, Status.ELIGIBLE_PROPOSED),
    EMPLOYEE_CREATE(Module.IDENTITY_EMPLOYEE, Status.ELIGIBLE_PROPOSED),
    EMPLOYEE_UPDATE(Module.IDENTITY_EMPLOYEE, Status.ELIGIBLE_PROPOSED),
    EMPLOYEE_LOCK(Module.IDENTITY_EMPLOYEE, Status.ELIGIBLE_PROPOSED),
    EMPLOYEE_UNLOCK(Module.IDENTITY_EMPLOYEE, Status.ELIGIBLE_PROPOSED),
    EMPLOYEE_ROLE_ASSIGN(Module.IDENTITY_EMPLOYEE, Status.ELIGIBLE_PROPOSED),

    // Role / Permission
    ROLE_READ(Module.ROLE_PERMISSION, Status.PROVISIONAL_DENY),
    PERMISSION_READ(Module.ROLE_PERMISSION, Status.PROVISIONAL_DENY),

    // Category
    CATEGORY_READ(Module.CATEGORY, Status.ELIGIBLE_PROPOSED),
    CATEGORY_CREATE(Module.CATEGORY, Status.ELIGIBLE_PROPOSED),
    CATEGORY_UPDATE(Module.CATEGORY, Status.ELIGIBLE_PROPOSED),
    CATEGORY_DEACTIVATE(Module.CATEGORY, Status.ELIGIBLE_PROPOSED),

    // Product
    PRODUCT_READ(Module.PRODUCT, Status.ELIGIBLE_PROPOSED),
    PRODUCT_CREATE(Module.PRODUCT, Status.ELIGIBLE_PROPOSED),
    PRODUCT_UPDATE(Module.PRODUCT, Status.ELIGIBLE_PROPOSED),
    PRODUCT_PRICE_UPDATE(Module.PRODUCT, Status.ELIGIBLE_PROPOSED),
    PRODUCT_DEACTIVATE(Module.PRODUCT, Status.ELIGIBLE_PROPOSED),

    // Supplier / Customer
    SUPPLIER_READ(Module.SUPPLIER, Status.ELIGIBLE_PROPOSED),
    SUPPLIER_CREATE(Module.SUPPLIER, Status.ELIGIBLE_PROPOSED),
    SUPPLIER_UPDATE(Module.SUPPLIER, Status.ELIGIBLE_PROPOSED),
    SUPPLIER_DEACTIVATE(Module.SUPPLIER, Status.ELIGIBLE_PROPOSED),
    CUSTOMER_READ(Module.CUSTOMER, Status.ELIGIBLE_PROPOSED),
    CUSTOMER_CREATE(Module.CUSTOMER, Status.ELIGIBLE_PROPOSED),
    CUSTOMER_UPDATE(Module.CUSTOMER, Status.ELIGIBLE_PROPOSED),
    CUSTOMER_DEACTIVATE(Module.CUSTOMER, Status.PROVISIONAL_DENY),

    // Purchasing
    PURCHASE_READ(Module.PURCHASING, Status.ELIGIBLE_PROPOSED),
    PURCHASE_CREATE(Module.PURCHASING, Status.ELIGIBLE_PROPOSED),
    PURCHASE_UPDATE(Module.PURCHASING, Status.ELIGIBLE_PROPOSED),
    PURCHASE_CONFIRM(Module.PURCHASING, Status.ELIGIBLE_PROPOSED),
    PURCHASE_CANCEL(Module.PURCHASING, Status.ELIGIBLE_PROPOSED),

    // Inventory / Ledger / Stocktake / Adjustment
    INVENTORY_READ(Module.INVENTORY, Status.ELIGIBLE_PROPOSED),
    INVENTORY_LEDGER_READ(Module.INVENTORY_LEDGER, Status.ELIGIBLE_PROPOSED),
    STOCKTAKE_READ(Module.STOCKTAKE, Status.ELIGIBLE_PROPOSED),
    STOCKTAKE_START(Module.STOCKTAKE, Status.ELIGIBLE_PROPOSED),
    STOCKTAKE_COUNT(Module.STOCKTAKE, Status.ELIGIBLE_PROPOSED),
    ADJUSTMENT_READ(Module.INVENTORY_ADJUSTMENT, Status.ELIGIBLE_PROPOSED),
    ADJUSTMENT_PROPOSE(Module.INVENTORY_ADJUSTMENT, Status.ELIGIBLE_PROPOSED),
    ADJUSTMENT_APPROVE(Module.INVENTORY_ADJUSTMENT, Status.ELIGIBLE_PROPOSED),
    ADJUSTMENT_REJECT(Module.INVENTORY_ADJUSTMENT, Status.PROVISIONAL_DENY),
    ADJUSTMENT_CANCEL(Module.INVENTORY_ADJUSTMENT, Status.PROVISIONAL_DENY),

    // Sales / Payment / Return / Refund
    SALE_READ(Module.SALES, Status.ELIGIBLE_PROPOSED),
    SALE_CREATE(Module.SALES, Status.ELIGIBLE_PROPOSED),
    SALE_UPDATE(Module.SALES, Status.ELIGIBLE_PROPOSED),
    SALE_CONFIRM(Module.SALES, Status.ELIGIBLE_PROPOSED),
    SALE_COMPLETE(Module.SALES, Status.PROVISIONAL_DENY),
    SALE_CANCEL(Module.SALES, Status.ELIGIBLE_PROPOSED),
    PAYMENT_READ(Module.PAYMENT, Status.ELIGIBLE_PROPOSED),
    PAYMENT_CREATE(Module.PAYMENT, Status.ELIGIBLE_PROPOSED),
    PAYMENT_VOID(Module.PAYMENT, Status.PROVISIONAL_DENY),
    RETURN_READ(Module.RETURN, Status.ELIGIBLE_PROPOSED),
    RETURN_CREATE(Module.RETURN, Status.ELIGIBLE_PROPOSED),
    RETURN_UPDATE(Module.RETURN, Status.ELIGIBLE_PROPOSED),
    RETURN_CONFIRM(Module.RETURN, Status.ELIGIBLE_PROPOSED),
    RETURN_CANCEL(Module.RETURN, Status.ELIGIBLE_PROPOSED),
    REFUND_READ(Module.REFUND, Status.PROVISIONAL_DENY),
    REFUND_CREATE(Module.REFUND, Status.PROVISIONAL_DENY),

    // Reports
    REPORT_REVENUE_READ(Module.REPORTS, Status.ELIGIBLE_PROPOSED),
    REPORT_SALES_READ(Module.REPORTS, Status.ELIGIBLE_PROPOSED),
    REPORT_INVENTORY_READ(Module.REPORTS, Status.ELIGIBLE_PROPOSED),
    REPORT_LOW_STOCK_READ(Module.REPORTS, Status.ELIGIBLE_PROPOSED);

    public enum Status {
        ELIGIBLE_PROPOSED,
        PROVISIONAL_DENY
    }

    public enum Module {
        IDENTITY_EMPLOYEE, ROLE_PERMISSION, CATEGORY, PRODUCT, SUPPLIER, CUSTOMER,
        PURCHASING, INVENTORY, INVENTORY_LEDGER, STOCKTAKE, INVENTORY_ADJUSTMENT,
        SALES, PAYMENT, RETURN, REFUND, REPORTS
    }

    private final Module module;
    private final Status status;

    PermissionCode(Module module, Status status) {
        this.module = module;
        this.status = status;
    }

    public String code() { return name(); }
    public Module module() { return module; }
    public Status status() { return status; }

    /** Exact lookup only; never normalize a misspelled or client-provided code into a permission. */
    public static Optional<PermissionCode> fromCode(String code) {
        if (code == null) return Optional.empty();
        try {
            return Optional.of(valueOf(code));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
