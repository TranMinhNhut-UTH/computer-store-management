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
@Table(name = "stocktake",
        uniqueConstraints = @UniqueConstraint(name = "uq_stocktake_stocktake_no", columnNames = "stocktake_no"),
        indexes = @Index(name = "ix_stocktake_status_started", columnList = "status, started_at"))
@Checks({
        @Check(name = "ck_stocktake_status", constraints = "status IN ('IN_PROGRESS', 'PENDING_APPROVAL', 'COMPLETED', 'CANCELLED')"),
        @Check(name = "ck_stocktake_cancel_fields", constraints = "(status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL AND cancel_reason IS NOT NULL) OR (status <> 'CANCELLED' AND cancelled_at IS NULL AND cancelled_by IS NULL AND cancel_reason IS NULL)")
})
public class Stocktake extends AbstractEntity {

    @Column(name = "stocktake_no", nullable = false, length = 40,
            columnDefinition = "VARCHAR(40) COLLATE utf8mb4_0900_bin")
    private String stocktakeNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private StocktakeStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by", nullable = false,
            foreignKey = @ForeignKey(name = "fk_stocktake_performed_by"))
    private AppUser performedBy;

    @Column(name = "started_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime startedAt;

    @Column(name = "completed_at", nullable = true, columnDefinition = "DATETIME(6)")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at", nullable = true, columnDefinition = "DATETIME(6)")
    private LocalDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by", nullable = true,
            foreignKey = @ForeignKey(name = "fk_stocktake_cancelled_by"))
    private AppUser cancelledBy;

    @Column(name = "cancel_reason", nullable = true, length = 500)
    private String cancelReason;

    @Column(name = "notes", nullable = true, length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected Stocktake() {
    }

    public String getStocktakeNo() { return stocktakeNo; }
    public void setStocktakeNo(String stocktakeNo) { this.stocktakeNo = stocktakeNo; }
    public StocktakeStatus getStatus() { return status; }
    public void setStatus(StocktakeStatus status) { this.status = status; }
    public AppUser getPerformedBy() { return performedBy; }
    public void setPerformedBy(AppUser performedBy) { this.performedBy = performedBy; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public AppUser getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(AppUser cancelledBy) { this.cancelledBy = cancelledBy; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
