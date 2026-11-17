package com.computerstore.computer_store_backend.identity.repository;

import com.computerstore.computer_store_backend.identity.entity.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppRoleRepository extends JpaRepository<AppRole, Long> {
    Optional<AppRole> findByCode(String code);
}
