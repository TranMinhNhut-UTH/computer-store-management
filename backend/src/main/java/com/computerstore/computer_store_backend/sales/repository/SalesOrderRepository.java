package com.computerstore.computer_store_backend.sales.repository;

import com.computerstore.computer_store_backend.sales.entity.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
}
