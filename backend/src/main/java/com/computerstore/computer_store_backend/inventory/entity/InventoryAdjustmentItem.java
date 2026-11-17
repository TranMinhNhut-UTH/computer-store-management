package com.computerstore.computer_store_backend.inventory.entity;

import com.computerstore.computer_store_backend.catalog.entity.Product;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_adjustment_item",
        uniqueConstraints = @UniqueConstraint(name = "uq_inventory_adjustment_item_adjustment_product", columnNames = {"adjustment_id", "product_id"}),
        indexes = @Index(name = "ix_inventory_adjustment_item_product", columnList = "product_id"))
@Check(name = "ck_inventory_adjustment_item_delta", constraints = "quantity_delta <> 0")
public class InventoryAdjustmentItem extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "adjustment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_adjustment_item_adjustment"))
    private InventoryAdjustment adjustment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_adjustment_item_product"))
    private Product product;

    @Column(name = "quantity_delta", nullable = false, columnDefinition = "BIGINT")
    private Long quantityDelta;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected InventoryAdjustmentItem() {
    }

    public InventoryAdjustment getAdjustment() { return adjustment; }
    public void setAdjustment(InventoryAdjustment adjustment) { this.adjustment = adjustment; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Long getQuantityDelta() { return quantityDelta; }
    public void setQuantityDelta(Long quantityDelta) { this.quantityDelta = quantityDelta; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
