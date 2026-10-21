package com.computerstore.repository;

import com.computerstore.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByCode(String code);
    boolean existsByCode(String code);
}
