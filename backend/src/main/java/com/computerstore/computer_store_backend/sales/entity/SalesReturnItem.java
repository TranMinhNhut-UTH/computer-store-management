package com.computerstore.computer_store_backend.sales.entity;

import com.computerstore.computer_store_backend.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Checks;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_return_item",
        uniqueConstraints = @UniqueConstraint(name = "uq_sales_return_item_return_order_item", columnNames = {"return_id", "sales_order_item_id"}),
        indexes = @Index(name = "ix_sales_return_item_order_item", columnList = "sales_order_item_id"))
@Checks({
        @Check(name = "ck_sales_return_item_quantity", constraints = "quantity > 0"),
        @Check(name = "ck_sales_return_item_restock", constraints = "restock_quantity >= 0 AND restock_quantity <= quantity"),
        @Check(name = "ck_sales_return_item_refund", constraints = "unit_refund_amount >= 0")
})
public class SalesReturnItem extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sales_return_item_return"))
    private SalesReturn salesReturn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_item_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sales_return_item_order_item"))
    private SalesOrderItem salesOrderItem;

    @Column(name = "quantity", nullable = false, columnDefinition = "BIGINT")
    private Long quantity;

    @Column(name = "restock_quantity", nullable = false, columnDefinition = "BIGINT")
    private Long restockQuantity = 0L;

    @Column(name = "unit_refund_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal unitRefundAmount;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected SalesReturnItem() {
    }

    public SalesReturn getSalesReturn() { return salesReturn; }
    public void setSalesReturn(SalesReturn salesReturn) { this.salesReturn = salesReturn; }
    public SalesOrderItem getSalesOrderItem() { return salesOrderItem; }
    public void setSalesOrderItem(SalesOrderItem salesOrderItem) { this.salesOrderItem = salesOrderItem; }
    public Long getQuantity() { return quantity; }
    public void setQuantity(Long quantity) { this.quantity = quantity; }
    public Long getRestockQuantity() { return restockQuantity; }
    public void setRestockQuantity(Long restockQuantity) { this.restockQuantity = restockQuantity; }
    public BigDecimal getUnitRefundAmount() { return unitRefundAmount; }
    public void setUnitRefundAmount(BigDecimal unitRefundAmount) { this.unitRefundAmount = unitRefundAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
