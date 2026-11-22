package com.computerstore.computer_store_backend.catalog.repository;

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

public interface ProductRepository extends JpaRepository<Product, Long> {
    String SEARCH_WHERE = " where (:active is null or p.active = :active)"
            + " and (:categoryId is null or c.id = :categoryId)"
            + " and (:pattern is null or p.sku like upper(:pattern) escape '!'"
            + " or p.name like :pattern escape '!' or p.brandName like :pattern escape '!'"
            + " or p.modelCode like :pattern escape '!')";

    Optional<Product> findBySku(String sku);
    List<Product> findAllByCategory_Id(Long categoryId);

    @Query(value = "select p from Product p join fetch p.category c" + SEARCH_WHERE,
            countQuery = "select count(p) from Product p join p.category c" + SEARCH_WHERE)
    Page<Product> search(@Param("pattern") String pattern, @Param("active") Boolean active,
            @Param("categoryId") Long categoryId, Pageable pageable);

    @Query("select p from Product p join fetch p.category where p.id = :id")
    Optional<Product> findDetail(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findForUpdate(@Param("id") long id);
}
