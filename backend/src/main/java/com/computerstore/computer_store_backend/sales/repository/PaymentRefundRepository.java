package com.computerstore.computer_store_backend.sales.repository;

import com.computerstore.computer_store_backend.sales.entity.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
}
