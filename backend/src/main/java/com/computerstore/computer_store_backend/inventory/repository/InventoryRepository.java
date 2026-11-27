package com.computerstore.computer_store_backend.inventory.repository;

import com.computerstore.computer_store_backend.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Query(value = "select i from Inventory i join fetch i.product p join fetch p.category c "
            + "where p.active = true",
            countQuery = "select count(i) from Inventory i where i.product.active = true")
    Page<Inventory> findActiveBalances(Pageable pageable);

    @Query(value = "select i from Inventory i join fetch i.product p join fetch p.category c "
            + "where p.active = true and i.quantityOnHand <= p.minStockLevel",
            countQuery = "select count(i) from Inventory i "
                    + "where i.product.active = true and i.quantityOnHand <= i.product.minStockLevel")
    Page<Inventory> findActiveLowStockBalances(Pageable pageable);

    @Query(value = "select i from Inventory i join fetch i.product p join fetch p.category c "
            + "where p.active = true and i.quantityOnHand = 0",
            countQuery = "select count(i) from Inventory i "
                    + "where i.product.active = true and i.quantityOnHand = 0")
    Page<Inventory> findActiveOutOfStockBalances(Pageable pageable);

    /** FND-009 §3 — khóa 1 dòng inventory trước khi đọc/ghi quantity_on_hand. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.product.id = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);

    /**
     * FND-009 §4 — Phương án A (PENDING, chưa chốt chính thức).
     * Khóa nhiều dòng theo product_id tăng dần trong 1 câu lệnh, dùng cho applySaleOut
     * nhiều SKU. KHÔNG dùng method này cho đến khi nhóm xác nhận chọn phương án A
     * (xem inventory-transaction-locking.md §4) thay vì phương án B (lặp tuần tự).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.product.id in :productIds order by i.product.id asc")
    List<Inventory> findAllByProductIdInForUpdate(@Param("productIds") List<Long> productIds);

    /** Hỗ trợ test DEP-001: mỗi Product phải có đúng 1 dòng Inventory. */
    boolean existsByProductId(Long productId);
}