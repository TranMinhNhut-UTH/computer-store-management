package com.computerstore.computer_store_backend.sales.repository;

import com.computerstore.computer_store_backend.sales.entity.SalesReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesReturnItemRepository extends JpaRepository<SalesReturnItem, Long> {
}
