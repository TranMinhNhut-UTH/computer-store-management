package com.computerstore.controller;

import com.computerstore.dto.PagedResult;
import com.computerstore.dto.StaffDtos.*;
import com.computerstore.dto.UserDto;
import com.computerstore.entity.RoleCodes;
import com.computerstore.entity.UserStatus;
import com.computerstore.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-05 – Chỉ Quản lý được tạo và quản lý tài khoản nhân viên. */
@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
@PreAuthorize("hasRole('QUAN_LY')")
public class StaffController {

    private final StaffService staff;

    @GetMapping
    public PagedResult<UserDto> list(@RequestParam(required = false) String search,
                                     @RequestParam(required = false) String roleCode,
                                     @RequestParam(required = false) UserStatus status,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return staff.list(search, roleCode, status, page, size);
    }

    @PostMapping
    public UserDto create(@Valid @RequestBody CreateStaffRequest req) {
        return staff.create(req);
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateStaffRequest req) {
        return staff.update(id, req);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> setStatus(@PathVariable Long id, @Valid @RequestBody UpdateStaffStatusRequest req) {
        staff.setStatus(id, req.status());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/roles")
    public List<String> roles() {
        return RoleCodes.STAFF;
    }
}
