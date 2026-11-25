package com.computerstore.computer_store_backend.identity.repository;

import com.computerstore.computer_store_backend.identity.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    Optional<AppUser> findByEmail(String email);

    @Query("""
            select u from AppUser u
            where (:active is null or u.active = :active)
              and (:pattern is null or u.username like :pattern escape '!' or u.fullName like :pattern escape '!')
            """)
    Page<AppUser> searchStaff(String pattern, Boolean active, Pageable pageable);
}
