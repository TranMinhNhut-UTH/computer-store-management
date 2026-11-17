package com.computerstore.computer_store_backend.sales.entity;

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
@Table(name = "sales_return",
        uniqueConstraints = @UniqueConstraint(name = "uq_sales_return_return_no", columnNames = "return_no"),
        indexes = {
                @Index(name = "ix_sales_return_order_status", columnList = "order_id, status"),
                @Index(name = "ix_sales_return_created", columnList = "created_at")
        })
@Checks({
        @Check(name = "ck_sales_return_status", constraints = "status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')"),
        @Check(name = "ck_sales_return_audit_pair", constraints = "confirmed_by IS NULL AND confirmed_at IS NULL OR confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL"),
        @Check(name = "ck_sales_return_transition", constraints = "(status = 'DRAFT' AND confirmed_by IS NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL) OR (status = 'CONFIRMED' AND confirmed_by IS NOT NULL AND cancelled_by IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL) OR (status = 'CANCELLED' AND confirmed_by IS NULL AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL)")
})
public class SalesReturn extends AbstractEntity {

    @Column(name = "return_no", nullable = false, length = 40,
            columnDefinition = "VARCHAR(40) COLLATE utf8mb4_0900_bin")
    private String returnNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sales_return_order"))
    private SalesOrder order;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private SalesReturnStatus status;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sales_return_created_by"))
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by", nullable = true,
            foreignKey = @ForeignKey(name = "fk_sales_return_confirmed_by"))
    private AppUser confirmedBy;

    @Column(name = "confirmed_at", nullable = true, columnDefinition = "DATETIME(6)")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at", nullable = true, columnDefinition = "DATETIME(6)")
    private LocalDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by", nullable = true,
            foreignKey = @ForeignKey(name = "fk_sales_return_cancelled_by"))
    private AppUser cancelledBy;

    @Column(name = "cancel_reason", nullable = true, length = 500)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected SalesReturn() {
    }

    public String getReturnNo() { return returnNo; }
    public void setReturnNo(String returnNo) { this.returnNo = returnNo; }
    public SalesOrder getOrder() { return order; }
    public void setOrder(SalesOrder order) { this.order = order; }
    public SalesReturnStatus getStatus() { return status; }
    public void setStatus(SalesReturnStatus status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public AppUser getCreatedBy() { return createdBy; }
    public void setCreatedBy(AppUser createdBy) { this.createdBy = createdBy; }
    public AppUser getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(AppUser confirmedBy) { this.confirmedBy = confirmedBy; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public AppUser getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(AppUser cancelledBy) { this.cancelledBy = cancelledBy; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
