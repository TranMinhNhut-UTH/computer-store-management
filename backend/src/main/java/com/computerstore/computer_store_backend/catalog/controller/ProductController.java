package com.computerstore.computer_store_backend.catalog.controller;

import com.computerstore.computer_store_backend.catalog.dto.request.ProductCreateRequest;
import com.computerstore.computer_store_backend.catalog.dto.request.ProductPriceRequest;
import com.computerstore.computer_store_backend.catalog.dto.request.ProductWriteRequest;
import com.computerstore.computer_store_backend.catalog.dto.response.ProductPageResponse;
import com.computerstore.computer_store_backend.catalog.dto.response.ProductResponse;
import com.computerstore.computer_store_backend.catalog.service.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService products;

    public ProductController(ProductService products) { this.products = products; }

    @GetMapping
    public ProductPageResponse list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) String categoryId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return products.list(q, active, categoryId, page, size);
    }

    @GetMapping("/{id}")
    public ProductResponse detail(@PathVariable String id) { return products.detail(id); }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest input) {
        var created = products.create(input);
        return ResponseEntity.created(URI.create("/api/v1/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable String id, @Valid @RequestBody ProductWriteRequest input) {
        return products.update(id, input);
    }

    @PutMapping("/{id}/price")
    public ProductResponse price(@PathVariable String id, @Valid @RequestBody ProductPriceRequest input) {
        return products.updatePrice(id, input);
    }

    @PostMapping("/{id}/deactivate")
    public ProductResponse deactivate(@PathVariable String id) { return products.deactivate(id); }
}
