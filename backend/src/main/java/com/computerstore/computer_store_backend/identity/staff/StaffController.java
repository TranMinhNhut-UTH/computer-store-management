package com.computerstore.computer_store_backend.identity.staff;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "csm.staff-management.enabled", havingValue = "true")
@RequestMapping("/api/v1/employees")
public class StaffController {
    private final StaffService staff;

    public StaffController(StaffService staff) {
        this.staff = staff;
    }

    @GetMapping
    public StaffResponse.Page list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return staff.list(q, active, page, size);
    }

    @GetMapping("/{id}")
    public StaffResponse detail(@PathVariable String id) { return staff.detail(id); }

    @PostMapping
    public ResponseEntity<StaffResponse> create(@Valid @RequestBody StaffRequests.Create request) {
        try {
            var created = staff.create(request);
            return ResponseEntity.created(URI.create("/api/v1/employees/" + created.id())).body(created);
        } finally {
            request.erasePassword();
        }
    }

    @PutMapping("/{id}")
    public StaffResponse update(@PathVariable String id, @Valid @RequestBody StaffRequests.Update request) {
        return staff.update(id, request);
    }

    @PutMapping("/{id}/roles")
    public StaffResponse roles(@PathVariable String id, @Valid @RequestBody StaffRequests.AssignRoles request) {
        return staff.assignRoles(id, request);
    }

    @PostMapping("/{id}/lock")
    public StaffResponse lock(@PathVariable String id) { return staff.setActive(id, false); }

    @PostMapping("/{id}/unlock")
    public StaffResponse unlock(@PathVariable String id) { return staff.setActive(id, true); }
}
