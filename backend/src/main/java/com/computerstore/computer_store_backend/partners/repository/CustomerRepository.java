package com.computerstore.computer_store_backend.partners.repository;

import com.computerstore.computer_store_backend.partners.entity.Customer;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    String SEARCH = " from Customer s where (:active is null or s.active = :active)"
            + " and (:pattern is null or s.code like upper(:pattern) escape '!' or s.fullName like :pattern escape '!')";

    Optional<Customer> findByCode(String code);

    @Query(value = "select s" + SEARCH, countQuery = "select count(s)" + SEARCH)
    Page<Customer> search(@Param("pattern") String pattern, @Param("active") Boolean active, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Customer s where s.id = :id")
    Optional<Customer> findForUpdate(@Param("id") Long id);
}
