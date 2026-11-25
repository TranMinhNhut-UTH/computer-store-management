package com.computerstore.computer_store_backend.identity.repository;

import com.computerstore.computer_store_backend.identity.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findAllByUser_Id(Long userId);
    boolean existsByUser_IdAndRole_Id(Long userId, Long roleId);

    @Query("select ur from UserRole ur join fetch ur.role where ur.user.id in :userIds")
    List<UserRole> findForStaff(Collection<Long> userIds);

    @Query("select count(distinct ur.user.id) from UserRole ur where ur.role.code = 'ADMIN' and ur.user.active = true")
    long activeAdmins();
}
