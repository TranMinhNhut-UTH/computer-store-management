package com.computerstore.computer_store_backend.inventory.repository;

import com.computerstore.computer_store_backend.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
}
