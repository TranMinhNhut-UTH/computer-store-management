package com.computerstore.computer_store_backend.inventory.repository;

import com.computerstore.computer_store_backend.inventory.entity.Stocktake;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StocktakeRepository extends JpaRepository<Stocktake, Long> {
}
