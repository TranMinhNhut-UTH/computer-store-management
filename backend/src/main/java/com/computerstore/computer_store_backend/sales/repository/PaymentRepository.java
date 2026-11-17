package com.computerstore.computer_store_backend.sales.repository;

import com.computerstore.computer_store_backend.sales.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
