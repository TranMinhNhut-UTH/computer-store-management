package com.computerstore.computer_store_backend.catalog;

import com.computerstore.computer_store_backend.catalog.entity.Category;
import java.time.LocalDateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

final class CategoryTestData {
    static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 20, 1, 2, 3);
    static Category category(long id, boolean active) {
        var value = BeanUtils.instantiateClass(Category.class);
        ReflectionTestUtils.setField(value, "id", id);
        value.setCode("CPU"); value.setName("Processors"); value.setDescription("Components");
        value.setActive(active); value.setCreatedAt(CREATED); value.setUpdatedAt(CREATED);
        return value;
    }
}
