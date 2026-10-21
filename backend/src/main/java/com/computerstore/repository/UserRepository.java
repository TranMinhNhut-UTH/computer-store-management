package com.computerstore.repository;

import com.computerstore.entity.User;
import com.computerstore.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);
    boolean existsByRoleCode(String roleCode);

    Optional<User> findByIdAndRoleCodeNot(Long id, String roleCode);

    @Query("""
            select u from User u
            where u.role.code <> :customerCode
              and (:roleCode is null or u.role.code = :roleCode)
              and (:status is null or u.status = :status)
              and (:search is null
                   or lower(u.fullName) like lower(concat('%', :search, '%'))
                   or lower(coalesce(u.email, '')) like lower(concat('%', :search, '%'))
                   or coalesce(u.phoneNumber, '') like concat('%', :search, '%'))
            """)
    Page<User> searchStaff(@Param("customerCode") String customerCode,
                           @Param("roleCode") String roleCode,
                           @Param("status") UserStatus status,
                           @Param("search") String search,
                           Pageable pageable);
}
