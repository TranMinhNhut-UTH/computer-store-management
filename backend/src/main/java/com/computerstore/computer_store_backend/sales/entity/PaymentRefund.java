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
@Table(name = "payment_refund",
        uniqueConstraints = @UniqueConstraint(name = "uq_payment_refund_refund_no", columnNames = "refund_no"),
        indexes = {
                @Index(name = "ix_payment_refund_payment_refunded", columnList = "payment_id, refunded_at"),
                @Index(name = "ix_payment_refund_return", columnList = "return_id")
        })
@Checks({
        @Check(name = "ck_payment_refund_amount", constraints = "amount > 0"),
        @Check(name = "ck_payment_refund_method", constraints = "method IN ('CASH', 'BANK_TRANSFER', 'CARD')")
})
public class PaymentRefund extends AbstractEntity {

    @Column(name = "refund_no", nullable = false, length = 40,
            columnDefinition = "VARCHAR(40) COLLATE utf8mb4_0900_bin")
    private String refundNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_refund_payment"))
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_payment_refund_return"))
    private SalesReturn salesReturn;

    @Column(name = "amount", nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30, columnDefinition = "VARCHAR(30)")
    private PaymentMethod method;

    @Column(name = "refunded_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime refundedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refunded_by", nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_refund_user"))
    private AppUser refundedBy;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected PaymentRefund() {
    }

    public String getRefundNo() { return refundNo; }
    public void setRefundNo(String refundNo) { this.refundNo = refundNo; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public SalesReturn getSalesReturn() { return salesReturn; }
    public void setSalesReturn(SalesReturn salesReturn) { this.salesReturn = salesReturn; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }
    public LocalDateTime getRefundedAt() { return refundedAt; }
    public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }
    public AppUser getRefundedBy() { return refundedBy; }
    public void setRefundedBy(AppUser refundedBy) { this.refundedBy = refundedBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
