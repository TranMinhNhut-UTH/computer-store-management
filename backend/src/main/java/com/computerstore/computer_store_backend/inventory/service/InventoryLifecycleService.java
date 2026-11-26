package com.computerstore.computer_store_backend.inventory.service;

import com.computerstore.computer_store_backend.catalog.entity.Product;
import com.computerstore.computer_store_backend.inventory.entity.Inventory;
import com.computerstore.computer_store_backend.inventory.repository.InventoryRepository;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FND-008 P-B (DEP-001) — tạo dòng tồn ban đầu (quantity_on_hand = 0) cho 1 Product MỚI.
 *
 * Chỉ dùng cho Product vừa được tạo, gọi đúng 1 lần ngay sau khi Product đã có id
 * (sau productRepository.save(product)). PHẢI được gọi bên trong cùng transaction với
 * việc tạo Product (Propagation.REQUIRED mặc định của @Transactional) — không bọc
 * try-catch nuốt lỗi ở ProductService, để lỗi ở đây rollback luôn cả Product vừa insert.
 */
@Service
public class InventoryLifecycleService {

    private final InventoryRepository inventoryRepository;
    private final UtcTimeProvider utcTimeProvider;

    public InventoryLifecycleService(InventoryRepository inventoryRepository, UtcTimeProvider utcTimeProvider) {
        this.inventoryRepository = inventoryRepository;
        this.utcTimeProvider = utcTimeProvider;
    }

    @Transactional
    public Inventory initializeForNewProduct(Product product) {
        Inventory inventory = BeanUtils.instantiateClass(Inventory.class);
        inventory.setProduct(product);
        inventory.setQuantityOnHand(0L);

        var now = utcTimeProvider.now();
        inventory.setCreatedAt(now);
        inventory.setUpdatedAt(now);

        return inventoryRepository.save(inventory);
    }
}
