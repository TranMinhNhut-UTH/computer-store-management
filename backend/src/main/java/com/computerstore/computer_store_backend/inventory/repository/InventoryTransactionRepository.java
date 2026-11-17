package com.computerstore.computer_store_backend.inventory.repository;

import com.computerstore.computer_store_backend.inventory.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
}
