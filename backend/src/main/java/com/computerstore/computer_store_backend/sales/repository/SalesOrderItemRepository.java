package com.computerstore.computer_store_backend.sales.repository;

import com.computerstore.computer_store_backend.sales.entity.SalesOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem, Long> {
}
