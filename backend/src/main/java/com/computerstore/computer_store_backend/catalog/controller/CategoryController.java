package com.computerstore.computer_store_backend.catalog.controller;

import com.computerstore.computer_store_backend.catalog.dto.request.CategoryWriteRequest;
import com.computerstore.computer_store_backend.catalog.dto.response.CategoryPageResponse;
import com.computerstore.computer_store_backend.catalog.dto.response.CategoryResponse;
import com.computerstore.computer_store_backend.catalog.service.CategoryService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Proposed Category V1 contract. Production SecurityConfig still denies every route here. */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryService categories;

    public CategoryController(CategoryService categories) { this.categories = categories; }

    @GetMapping
    public CategoryPageResponse list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return categories.list(q, active, page, size);
    }

    @GetMapping("/{id}")
    public CategoryResponse detail(@PathVariable String id) { return categories.detail(id); }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryWriteRequest input) {
        var created = categories.create(input);
        return ResponseEntity.created(URI.create("/api/v1/categories/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable String id, @Valid @RequestBody CategoryWriteRequest input) {
        return categories.update(id, input);
    }

    @PostMapping("/{id}/deactivate")
    public CategoryResponse deactivate(@PathVariable String id) { return categories.deactivate(id); }
}
