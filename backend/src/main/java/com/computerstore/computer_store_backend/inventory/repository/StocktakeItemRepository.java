package com.computerstore.computer_store_backend.inventory.repository;

import com.computerstore.computer_store_backend.inventory.entity.StocktakeItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StocktakeItemRepository extends JpaRepository<StocktakeItem, Long> {
}
