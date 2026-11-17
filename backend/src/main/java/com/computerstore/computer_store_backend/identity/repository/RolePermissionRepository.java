package com.computerstore.computer_store_backend.identity.repository;

import com.computerstore.computer_store_backend.identity.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findAllByRole_Id(Long roleId);
    boolean existsByRole_IdAndPermission_Id(Long roleId, Long permissionId);
}
