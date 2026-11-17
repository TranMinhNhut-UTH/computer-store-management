package com.computerstore.computer_store_backend.sales.repository;

import com.computerstore.computer_store_backend.sales.entity.SalesReturn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {
}
