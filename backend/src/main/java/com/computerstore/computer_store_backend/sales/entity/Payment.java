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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment",
        uniqueConstraints = @UniqueConstraint(name = "uq_payment_payment_no", columnNames = "payment_no"),
        indexes = @Index(name = "ix_payment_order_paid", columnList = "order_id, paid_at"))
@Checks({
        @Check(name = "ck_payment_amount", constraints = "amount > 0"),
        @Check(name = "ck_payment_method", constraints = "method IN ('CASH', 'BANK_TRANSFER', 'CARD')"),
        @Check(name = "ck_payment_status", constraints = "status IN ('RECORDED', 'VOIDED')"),
        @Check(name = "ck_payment_void_fields", constraints = "(status = 'RECORDED' AND voided_at IS NULL AND voided_by IS NULL AND void_reason IS NULL) OR (status = 'VOIDED' AND voided_at IS NOT NULL AND voided_by IS NOT NULL AND void_reason IS NOT NULL)")
})
public class Payment extends AbstractEntity {

    @Column(name = "payment_no", nullable = false, length = 40,
            columnDefinition = "VARCHAR(40) COLLATE utf8mb4_0900_bin")
    private String paymentNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_order"))
    private SalesOrder order;

    @Column(name = "amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30, columnDefinition = "VARCHAR(30)")
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private PaymentRecordStatus status;

    @Column(name = "paid_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime paidAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_recorded_by"))
    private AppUser recordedBy;

    @Column(name = "voided_at", nullable = true, columnDefinition = "DATETIME(6)")
    private LocalDateTime voidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voided_by", nullable = true,
            foreignKey = @ForeignKey(name = "fk_payment_voided_by"))
    private AppUser voidedBy;

    @Column(name = "void_reason", nullable = true, length = 500)
    private String voidReason;

    @Column(name = "reference_note", nullable = true, length = 255)
    private String referenceNote;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected Payment() {
    }

    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }
    public SalesOrder getOrder() { return order; }
    public void setOrder(SalesOrder order) { this.order = order; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }
    public PaymentRecordStatus getStatus() { return status; }
    public void setStatus(PaymentRecordStatus status) { this.status = status; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public AppUser getRecordedBy() { return recordedBy; }
    public void setRecordedBy(AppUser recordedBy) { this.recordedBy = recordedBy; }
    public LocalDateTime getVoidedAt() { return voidedAt; }
    public void setVoidedAt(LocalDateTime voidedAt) { this.voidedAt = voidedAt; }
    public AppUser getVoidedBy() { return voidedBy; }
    public void setVoidedBy(AppUser voidedBy) { this.voidedBy = voidedBy; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
    public String getReferenceNote() { return referenceNote; }
    public void setReferenceNote(String referenceNote) { this.referenceNote = referenceNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
