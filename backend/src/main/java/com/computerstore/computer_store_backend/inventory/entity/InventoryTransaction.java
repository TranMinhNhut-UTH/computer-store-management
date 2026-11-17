package com.computerstore.computer_store_backend.inventory.entity;

import com.computerstore.computer_store_backend.catalog.entity.Product;
import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.purchasing.entity.PurchaseItem;
import com.computerstore.computer_store_backend.sales.entity.SalesOrderItem;
import com.computerstore.computer_store_backend.sales.entity.SalesReturnItem;
import com.computerstore.computer_store_backend.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Checks;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_transaction",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_inventory_transaction_purchase_item", columnNames = "purchase_item_id"),
                @UniqueConstraint(name = "uq_inventory_transaction_sales_item", columnNames = "sales_order_item_id"),
                @UniqueConstraint(name = "uq_inventory_transaction_return_item", columnNames = "sales_return_item_id"),
                @UniqueConstraint(name = "uq_inventory_transaction_adjustment_item", columnNames = "adjustment_item_id"),
                @UniqueConstraint(name = "uq_inventory_transaction_reversal", columnNames = "reversal_of_transaction_id")
        },
        indexes = {
                @Index(name = "ix_inventory_transaction_product_occurred", columnList = "product_id, occurred_at"),
                @Index(name = "ix_inventory_transaction_type_occurred", columnList = "transaction_type, occurred_at")
        })
@Checks({
        @Check(name = "ck_inventory_transaction_type", constraints = "transaction_type IN ('PURCHASE_IN', 'SALE_OUT', 'RETURN_IN', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'SALE_CANCEL_IN')"),
        @Check(name = "ck_inventory_transaction_delta", constraints = "quantity_delta <> 0"),
        @Check(name = "ck_inventory_transaction_after", constraints = "quantity_after >= 0"),
        @Check(name = "ck_inventory_transaction_source", constraints = "(reversal_of_transaction_id IS NULL AND ((purchase_item_id IS NOT NULL) + (sales_order_item_id IS NOT NULL) + (sales_return_item_id IS NOT NULL) + (adjustment_item_id IS NOT NULL)) = 1 AND reversal_reason IS NULL AND transaction_type <> 'SALE_CANCEL_IN') OR (reversal_of_transaction_id IS NOT NULL AND purchase_item_id IS NULL AND sales_order_item_id IS NULL AND sales_return_item_id IS NULL AND adjustment_item_id IS NULL AND reversal_reason IS NOT NULL AND transaction_type = 'SALE_CANCEL_IN')")
})
public class InventoryTransaction extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_transaction_product"))
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30, columnDefinition = "VARCHAR(30)")
    private InventoryTransactionType transactionType;

    @Column(name = "quantity_delta", nullable = false, columnDefinition = "BIGINT")
    private Long quantityDelta;

    @Column(name = "quantity_after", nullable = false, columnDefinition = "BIGINT")
    private Long quantityAfter;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime occurredAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_transaction_user"))
    private AppUser recordedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_item_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_inventory_transaction_purchase_item"))
    private PurchaseItem purchaseItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_item_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_inventory_transaction_sales_item"))
    private SalesOrderItem salesOrderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_return_item_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_inventory_transaction_return_item"))
    private SalesReturnItem salesReturnItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_item_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_inventory_transaction_adjustment_item"))
    private InventoryAdjustmentItem adjustmentItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_transaction_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_inventory_transaction_reversal"))
    private InventoryTransaction reversalOfTransaction;

    @Column(name = "reversal_reason", nullable = true, length = 500)
    private String reversalReason;

    @Column(name = "note", nullable = true, length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected InventoryTransaction() {
    }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public InventoryTransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(InventoryTransactionType transactionType) { this.transactionType = transactionType; }
    public Long getQuantityDelta() { return quantityDelta; }
    public void setQuantityDelta(Long quantityDelta) { this.quantityDelta = quantityDelta; }
    public Long getQuantityAfter() { return quantityAfter; }
    public void setQuantityAfter(Long quantityAfter) { this.quantityAfter = quantityAfter; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public AppUser getRecordedBy() { return recordedBy; }
    public void setRecordedBy(AppUser recordedBy) { this.recordedBy = recordedBy; }
    public PurchaseItem getPurchaseItem() { return purchaseItem; }
    public void setPurchaseItem(PurchaseItem purchaseItem) { this.purchaseItem = purchaseItem; }
    public SalesOrderItem getSalesOrderItem() { return salesOrderItem; }
    public void setSalesOrderItem(SalesOrderItem salesOrderItem) { this.salesOrderItem = salesOrderItem; }
    public SalesReturnItem getSalesReturnItem() { return salesReturnItem; }
    public void setSalesReturnItem(SalesReturnItem salesReturnItem) { this.salesReturnItem = salesReturnItem; }
    public InventoryAdjustmentItem getAdjustmentItem() { return adjustmentItem; }
    public void setAdjustmentItem(InventoryAdjustmentItem adjustmentItem) { this.adjustmentItem = adjustmentItem; }
    public InventoryTransaction getReversalOfTransaction() { return reversalOfTransaction; }
    public void setReversalOfTransaction(InventoryTransaction reversalOfTransaction) { this.reversalOfTransaction = reversalOfTransaction; }
    public String getReversalReason() { return reversalReason; }
    public void setReversalReason(String reversalReason) { this.reversalReason = reversalReason; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
