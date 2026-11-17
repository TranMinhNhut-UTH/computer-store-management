package com.computerstore.computer_store_backend.partners.repository;

import com.computerstore.computer_store_backend.partners.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByCode(String code);
}
