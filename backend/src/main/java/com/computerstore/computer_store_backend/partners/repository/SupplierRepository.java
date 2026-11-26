package com.computerstore.computer_store_backend.partners.repository;

import com.computerstore.computer_store_backend.partners.entity.Supplier;
import com.computerstore.computer_store_backend.purchasing.entity.Purchase;
import com.computerstore.computer_store_backend.purchasing.entity.PurchaseStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    String SEARCH = " from Supplier s where (:active is null or s.active = :active)"
            + " and (:pattern is null or s.code like upper(:pattern) escape '!' or s.name like :pattern escape '!')";

    Optional<Supplier> findByCode(String code);

    @Query(value = "select s" + SEARCH, countQuery = "select count(s)" + SEARCH)
    Page<Supplier> search(@Param("pattern") String pattern, @Param("active") Boolean active, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Supplier s where s.id = :id")
    Optional<Supplier> findForUpdate(@Param("id") Long id);

    // Read the database value rather than a Supplier already managed by the caller's persistence context.
    @Query(value = "select case when is_active = 1 then 1 else 0 end from supplier where id = :id for update",
            nativeQuery = true)
    Optional<Integer> lockActiveState(@Param("id") long id);

    // Run after locking the supplier. The locking read sees drafts committed before that lock.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from Purchase p where p.supplier.id = :id and p.status = :status")
    List<Purchase> findPurchases(@Param("id") Long id,
            @Param("status") PurchaseStatus status, Pageable limit);
}
