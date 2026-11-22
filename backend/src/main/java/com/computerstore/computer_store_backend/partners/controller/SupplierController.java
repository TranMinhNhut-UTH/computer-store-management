package com.computerstore.computer_store_backend.partners.controller;

import com.computerstore.computer_store_backend.partners.dto.SupplierPageResponse;
import com.computerstore.computer_store_backend.partners.dto.SupplierResponse;
import com.computerstore.computer_store_backend.partners.dto.SupplierWriteRequest;
import com.computerstore.computer_store_backend.partners.service.SupplierService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {
    private final SupplierService suppliers;

    public SupplierController(SupplierService suppliers) { this.suppliers = suppliers; }

    @GetMapping
    public SupplierPageResponse list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return suppliers.list(q, active, page, size);
    }

    @GetMapping("/{id}")
    public SupplierResponse detail(@PathVariable String id) { return suppliers.detail(id); }

    @PostMapping
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierWriteRequest input) {
        var supplier = suppliers.create(input);
        return ResponseEntity.created(URI.create("/api/v1/suppliers/" + supplier.id())).body(supplier);
    }

    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable String id, @Valid @RequestBody SupplierWriteRequest input) {
        return suppliers.update(id, input);
    }

    @PostMapping("/{id}/deactivate")
    public SupplierResponse deactivate(@PathVariable String id) { return suppliers.deactivate(id); }
}
