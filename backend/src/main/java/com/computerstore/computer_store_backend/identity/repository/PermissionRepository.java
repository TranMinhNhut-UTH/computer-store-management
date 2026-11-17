package com.computerstore.computer_store_backend.identity.repository;

import com.computerstore.computer_store_backend.identity.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
}
