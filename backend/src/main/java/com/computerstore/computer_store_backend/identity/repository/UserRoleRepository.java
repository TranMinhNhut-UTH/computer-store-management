package com.computerstore.computer_store_backend.identity.repository;

import com.computerstore.computer_store_backend.identity.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findAllByUser_Id(Long userId);
    boolean existsByUser_IdAndRole_Id(Long userId, Long roleId);
}
