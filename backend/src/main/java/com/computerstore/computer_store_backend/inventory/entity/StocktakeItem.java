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
import org.hibernate.annotations.Checks;

import java.time.LocalDateTime;

@Entity
@Table(name = "stocktake_item",
        uniqueConstraints = @UniqueConstraint(name = "uq_stocktake_item_stocktake_product", columnNames = {"stocktake_id", "product_id"}),
        indexes = @Index(name = "ix_stocktake_item_product", columnList = "product_id"))
@Checks({
        @Check(name = "ck_stocktake_item_system_qty", constraints = "system_quantity >= 0"),
        @Check(name = "ck_stocktake_item_counted_qty", constraints = "counted_quantity IS NULL OR counted_quantity >= 0"),
        @Check(name = "ck_stocktake_item_count_pair", constraints = "counted_quantity IS NULL AND counted_at IS NULL OR counted_quantity IS NOT NULL AND counted_at IS NOT NULL")
})
public class StocktakeItem extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stocktake_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_stocktake_item_stocktake"))
    private Stocktake stocktake;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_stocktake_item_product"))
    private Product product;

    @Column(name = "system_quantity", nullable = false, columnDefinition = "BIGINT")
    private Long systemQuantity;

    @Column(name = "snapshot_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime snapshotAt;

    @Column(name = "counted_quantity", nullable = true, columnDefinition = "BIGINT")
    private Long countedQuantity;

    @Column(name = "counted_at", nullable = true, columnDefinition = "DATETIME(6)")
    private LocalDateTime countedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected StocktakeItem() {
    }

    public Stocktake getStocktake() { return stocktake; }
    public void setStocktake(Stocktake stocktake) { this.stocktake = stocktake; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Long getSystemQuantity() { return systemQuantity; }
    public void setSystemQuantity(Long systemQuantity) { this.systemQuantity = systemQuantity; }
    public LocalDateTime getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(LocalDateTime snapshotAt) { this.snapshotAt = snapshotAt; }
    public Long getCountedQuantity() { return countedQuantity; }
    public void setCountedQuantity(Long countedQuantity) { this.countedQuantity = countedQuantity; }
    public LocalDateTime getCountedAt() { return countedAt; }
    public void setCountedAt(LocalDateTime countedAt) { this.countedAt = countedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
