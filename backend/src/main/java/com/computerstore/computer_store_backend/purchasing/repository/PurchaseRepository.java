package com.computerstore.computer_store_backend.purchasing.repository;

import com.computerstore.computer_store_backend.purchasing.entity.Purchase;
import com.computerstore.computer_store_backend.purchasing.entity.PurchaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    Optional<Purchase> findByDocumentNo(String documentNo);
    List<Purchase> findAllByStatusOrderByCreatedAtDesc(PurchaseStatus status);
}
