package com.computerstore.computer_store_backend.inventory.repository;

import com.computerstore.computer_store_backend.inventory.entity.InventoryAdjustmentItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryAdjustmentItemRepository extends JpaRepository<InventoryAdjustmentItem, Long> {
}
