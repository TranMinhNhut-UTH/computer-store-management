package com.computerstore.computer_store_backend.inventory.entity;

import com.computerstore.computer_store_backend.identity.entity.AppUser;
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
@Table(name = "inventory_adjustment",
        uniqueConstraints = @UniqueConstraint(name = "uq_inventory_adjustment_no", columnNames = "adjustment_no"),
        indexes = {
                @Index(name = "ix_inventory_adjustment_status_proposed", columnList = "status, proposed_at"),
                @Index(name = "ix_inventory_adjustment_stocktake", columnList = "stocktake_id")
        })
@Checks({
        @Check(name = "ck_inventory_adjustment_status", constraints = "status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')"),
        @Check(name = "ck_inventory_adjustment_approval_pair", constraints = "approved_by IS NULL AND approved_at IS NULL OR approved_by IS NOT NULL AND approved_at IS NOT NULL"),
        @Check(name = "ck_inventory_adjustment_approver", constraints = "approved_by IS NULL OR approved_by <> proposed_by"),
        @Check(name = "ck_inventory_adjustment_transition", constraints = "(status = 'PENDING' AND approved_by IS NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL) OR (status = 'APPROVED' AND approved_by IS NOT NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL) OR (status = 'REJECTED' AND approved_by IS NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL) OR (status = 'CANCELLED' AND approved_by IS NULL AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL)")
})
public class InventoryAdjustment extends AbstractEntity {

    @Column(name = "adjustment_no", nullable = false, length = 40,
            columnDefinition = "VARCHAR(40) COLLATE utf8mb4_0900_bin")
    private String adjustmentNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private InventoryAdjustmentStatus status;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposed_by", nullable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_adjustment_proposed_by"))
    private AppUser proposedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by", nullable = true,
            foreignKey = @ForeignKey(name = "fk_inventory_adjustment_approved_by"))
    private AppUser approvedBy;

    @Column(name = "proposed_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime proposedAt;

    @Column(name = "approved_at", nullable = true, columnDefinition = "DATETIME(6)")
    private LocalDateTime approvedAt;

    @Column(name = "cancelled_at", nullable = true, columnDefinition = "DATETIME(6)")
    private LocalDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by", nullable = true,
            foreignKey = @ForeignKey(name = "fk_inventory_adjustment_cancelled_by"))
    private AppUser cancelledBy;

    @Column(name = "cancel_reason", nullable = true, length = 500)
    private String cancelReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stocktake_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_inventory_adjustment_stocktake"))
    private Stocktake stocktake;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected InventoryAdjustment() {
    }

    public String getAdjustmentNo() { return adjustmentNo; }
    public void setAdjustmentNo(String adjustmentNo) { this.adjustmentNo = adjustmentNo; }
    public InventoryAdjustmentStatus getStatus() { return status; }
    public void setStatus(InventoryAdjustmentStatus status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public AppUser getProposedBy() { return proposedBy; }
    public void setProposedBy(AppUser proposedBy) { this.proposedBy = proposedBy; }
    public AppUser getApprovedBy() { return approvedBy; }
    public void setApprovedBy(AppUser approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getProposedAt() { return proposedAt; }
    public void setProposedAt(LocalDateTime proposedAt) { this.proposedAt = proposedAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public AppUser getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(AppUser cancelledBy) { this.cancelledBy = cancelledBy; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public Stocktake getStocktake() { return stocktake; }
    public void setStocktake(Stocktake stocktake) { this.stocktake = stocktake; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
