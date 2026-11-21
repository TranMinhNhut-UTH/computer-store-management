package com.computerstore.computer_store_backend.catalog.repository;

import com.computerstore.computer_store_backend.catalog.entity.Category;
import com.computerstore.computer_store_backend.catalog.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    String SEARCH_WHERE = " where (:active is null or c.active = :active)"
            + " and (:pattern is null or c.code like upper(:pattern) escape '!' or c.name like :pattern escape '!')";
    String SEARCH_QUERY = "select c from Category c" + SEARCH_WHERE;
    String COUNT_QUERY = "select count(c) from Category c" + SEARCH_WHERE;
    String LOCK_QUERY = "select c from Category c where c.id = :id";
    String REFERENCES_QUERY = "select p from Product p where p.category.id = :categoryId";

    Optional<Category> findByCode(String code);
    List<Category> findAllByActiveTrueOrderByNameAsc();

    @Query(value = SEARCH_QUERY, countQuery = COUNT_QUERY)
    Page<Category> search(@Param("pattern") String pattern, @Param("active") Boolean active, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(LOCK_QUERY)
    Optional<Category> findForUpdate(@Param("id") Long id);

    // Bounded internal integrity read, never a Product API or permission grant.
    // A locking read avoids relying on the earlier authorization queries' repeatable-read snapshot.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query(REFERENCES_QUERY)
    List<Product> findProductReferencesForDeactivation(@Param("categoryId") Long categoryId, Pageable limit);
}
