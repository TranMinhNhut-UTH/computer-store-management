package com.computerstore.computer_store_backend.sales.entity;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_order_item",
        uniqueConstraints = @UniqueConstraint(name = "uq_sales_order_item_order_product", columnNames = {"order_id", "product_id"}),
        indexes = @Index(name = "ix_sales_order_item_product", columnList = "product_id"))
@Checks({
        @Check(name = "ck_sales_order_item_quantity", constraints = "quantity > 0"),
        @Check(name = "ck_sales_order_item_unit_price", constraints = "unit_price >= 0"),
        @Check(name = "ck_sales_order_item_line_total", constraints = "line_total >= 0")
})
public class SalesOrderItem extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sales_order_item_order"))
    private SalesOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sales_order_item_product"))
    private Product product;

    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    private String productNameSnapshot;

    @Column(name = "sku_snapshot", nullable = false, length = 64)
    private String skuSnapshot;

    @Column(name = "brand_snapshot", nullable = true, length = 100)
    private String brandSnapshot;

    @Column(name = "model_snapshot", nullable = true, length = 100)
    private String modelSnapshot;

    @Column(name = "quantity", nullable = false, columnDefinition = "BIGINT")
    private Long quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 0)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 0)
    private BigDecimal lineTotal;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected SalesOrderItem() {
    }

    public SalesOrder getOrder() { return order; }
    public void setOrder(SalesOrder order) { this.order = order; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public void setProductNameSnapshot(String productNameSnapshot) { this.productNameSnapshot = productNameSnapshot; }
    public String getSkuSnapshot() { return skuSnapshot; }
    public void setSkuSnapshot(String skuSnapshot) { this.skuSnapshot = skuSnapshot; }
    public String getBrandSnapshot() { return brandSnapshot; }
    public void setBrandSnapshot(String brandSnapshot) { this.brandSnapshot = brandSnapshot; }
    public String getModelSnapshot() { return modelSnapshot; }
    public void setModelSnapshot(String modelSnapshot) { this.modelSnapshot = modelSnapshot; }
    public Long getQuantity() { return quantity; }
    public void setQuantity(Long quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
