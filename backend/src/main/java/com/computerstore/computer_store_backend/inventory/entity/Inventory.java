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
import jakarta.persistence.Version;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Checks;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory",
        uniqueConstraints = @UniqueConstraint(name = "uq_inventory_product", columnNames = "product_id"))
@Checks({
        @Check(name = "ck_inventory_quantity", constraints = "quantity_on_hand >= 0"),
        @Check(name = "ck_inventory_version", constraints = "version >= 0")
})
public class Inventory extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_product"))
    private Product product;

    @Column(name = "quantity_on_hand", nullable = false, columnDefinition = "BIGINT")
    private Long quantityOnHand = 0L;

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT")
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected Inventory() {
    }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Long getQuantityOnHand() { return quantityOnHand; }
    public void setQuantityOnHand(Long quantityOnHand) { this.quantityOnHand = quantityOnHand; }
    public Long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
