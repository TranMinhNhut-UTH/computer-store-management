package com.computerstore.computer_store_backend.catalog.entity;

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
@Table(name = "product",
        uniqueConstraints = @UniqueConstraint(name = "uq_product_sku", columnNames = "sku"),
        indexes = {
                @Index(name = "ix_product_category_active", columnList = "category_id, is_active"),
                @Index(name = "ix_product_name", columnList = "name"),
                @Index(name = "ix_product_brand", columnList = "brand_name"),
                @Index(name = "ix_product_model", columnList = "model_code")
        })
@Checks({
        @Check(name = "ck_product_selling_price", constraints = "selling_price >= 0"),
        @Check(name = "ck_product_min_stock", constraints = "min_stock_level >= 0"),
        @Check(name = "ck_product_active", constraints = "is_active IN (0, 1)")
})
public class Product extends AbstractEntity {

    @Column(name = "sku", nullable = false, length = 64,
            columnDefinition = "VARCHAR(64) COLLATE utf8mb4_0900_bin")
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "brand_name", nullable = true, length = 100)
    private String brandName;

    @Column(name = "model_code", nullable = true, length = 100)
    private String modelCode;

    @Column(name = "description", nullable = true, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_product_category"))
    private Category category;

    @Column(name = "selling_price", nullable = false, precision = 19, scale = 0)
    private BigDecimal sellingPrice;

    @Column(name = "min_stock_level", nullable = false, columnDefinition = "BIGINT")
    private Long minStockLevel = 0L;

    @Column(name = "is_active", nullable = false, columnDefinition = "BOOLEAN")
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected Product() {
    }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(BigDecimal sellingPrice) { this.sellingPrice = sellingPrice; }
    public Long getMinStockLevel() { return minStockLevel; }
    public void setMinStockLevel(Long minStockLevel) { this.minStockLevel = minStockLevel; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
