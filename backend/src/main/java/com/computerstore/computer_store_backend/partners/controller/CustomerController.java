package com.computerstore.computer_store_backend.partners.controller;

import com.computerstore.computer_store_backend.partners.dto.CustomerPageResponse;
import com.computerstore.computer_store_backend.partners.dto.CustomerResponse;
import com.computerstore.computer_store_backend.partners.dto.CustomerWriteRequest;
import com.computerstore.computer_store_backend.partners.service.CustomerService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerService customers;

    public CustomerController(CustomerService customers) { this.customers = customers; }

    @GetMapping
    public CustomerPageResponse list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return customers.list(q, active, page, size);
    }

    @GetMapping("/{id}")
    public CustomerResponse detail(@PathVariable String id) { return customers.detail(id); }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerWriteRequest input) {
        var customer = customers.create(input);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.id())).body(customer);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable String id, @Valid @RequestBody CustomerWriteRequest input) {
        return customers.update(id, input);
    }
}
