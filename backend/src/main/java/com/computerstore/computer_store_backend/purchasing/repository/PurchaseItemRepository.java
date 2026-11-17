package com.computerstore.computer_store_backend.purchasing.repository;

import com.computerstore.computer_store_backend.purchasing.entity.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {
    List<PurchaseItem> findAllByPurchase_IdOrderByIdAsc(Long purchaseId);
}
