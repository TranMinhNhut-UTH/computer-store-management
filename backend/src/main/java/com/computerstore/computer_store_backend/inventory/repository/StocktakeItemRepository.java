package com.computerstore.computer_store_backend.inventory.repository;

import com.computerstore.computer_store_backend.inventory.entity.StocktakeItem;
import com.computerstore.computer_store_backend.inventory.entity.StocktakeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StocktakeItemRepository extends JpaRepository<StocktakeItem, Long> {

    /** database-design.md §4.5 — chặn movement khi SKU đang nằm trong 1 stocktake chưa đếm xong. */
    boolean existsByProductIdAndCountedAtIsNullAndStocktake_Status(Long productId, StocktakeStatus status);
}