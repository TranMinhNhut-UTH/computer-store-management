package com.computerstore.computer_store_backend.purchasing.entity;

import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.partners.entity.Supplier;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "purchase",
        uniqueConstraints = @UniqueConstraint(name = "uq_purchase_document_no", columnNames = "document_no"),
        indexes = {
                @Index(name = "ix_purchase_status_created", columnList = "status, created_at"),
                @Index(name = "ix_purchase_supplier_created", columnList = "supplier_id, created_at")
        })
@Checks({
        @Check(name = "ck_purchase_total", constraints = "total_amount >= 0"),
        @Check(name = "ck_purchase_status", constraints = "status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')"),
        @Check(name = "ck_purchase_transition", constraints = "(status = 'DRAFT' AND confirmed_by IS NULL AND confirmed_at IS NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL) OR (status = 'CONFIRMED' AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL) OR (status = 'CANCELLED' AND confirmed_by IS NULL AND confirmed_at IS NULL AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL)")
})
public class Purchase extends AbstractEntity {

    @Column(name = "document_no", nullable = false, length = 40,
            columnDefinition = "VARCHAR(40) COLLATE utf8mb4_0900_bin")
    private String documentNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_supplier"))
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private PurchaseStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false,
            foreignKey = @ForeignKey(name = "fk_purchase_created_by"))
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by", nullable = true,
            foreignKey = @ForeignKey(name = "fk_purchase_confirmed_by"))
    private AppUser confirmedBy;

    @Column(name = "confirmed_at", nullable = true, columnDefinition = "DATETIME(6)")
    private LocalDateTime confirmedAt;

    @Column(name = "notes", nullable = true, length = 1000)
    private String notes;

    @Column(name = "cancelled_at", nullable = true, columnDefinition = "DATETIME(6)")
    private LocalDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by", nullable = true,
            foreignKey = @ForeignKey(name = "fk_purchase_cancelled_by"))
    private AppUser cancelledBy;

    @Column(name = "cancel_reason", nullable = true, length = 500)
    private String cancelReason;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected Purchase() {
    }

    public String getDocumentNo() { return documentNo; }
    public void setDocumentNo(String documentNo) { this.documentNo = documentNo; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public PurchaseStatus getStatus() { return status; }
    public void setStatus(PurchaseStatus status) { this.status = status; }
    public AppUser getCreatedBy() { return createdBy; }
    public void setCreatedBy(AppUser createdBy) { this.createdBy = createdBy; }
    public AppUser getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(AppUser confirmedBy) { this.confirmedBy = confirmedBy; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public AppUser getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(AppUser cancelledBy) { this.cancelledBy = cancelledBy; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
