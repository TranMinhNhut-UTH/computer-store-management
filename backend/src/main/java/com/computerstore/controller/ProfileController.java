package com.computerstore.controller;

import com.computerstore.dto.ProfileDtos.*;
import com.computerstore.dto.UserDto;
import com.computerstore.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-04 – Hồ sơ cá nhân, đổi mật khẩu, sổ địa chỉ (mọi người dùng đã đăng nhập). */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profile;

    @GetMapping
    public UserDto get() {
        return profile.get();
    }

    @PutMapping
    public UserDto update(@Valid @RequestBody UpdateProfileRequest req) {
        return profile.update(req);
    }

    /** Sau khi đổi, mọi thiết bị bị đăng xuất; client cần đăng nhập lại. */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        profile.changePassword(req);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/addresses")
    public List<AddressDto> addresses() {
        return profile.listAddresses();
    }

    @PostMapping("/addresses")
    public AddressDto createAddress(@Valid @RequestBody AddressRequest req) {
        return profile.createAddress(req);
    }

    @PutMapping("/addresses/{id}")
    public AddressDto updateAddress(@PathVariable Long id, @Valid @RequestBody AddressRequest req) {
        return profile.updateAddress(id, req);
    }

    @PutMapping("/addresses/{id}/default")
    public ResponseEntity<Void> setDefault(@PathVariable Long id) {
        profile.setDefaultAddress(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id) {
        profile.deleteAddress(id);
        return ResponseEntity.noContent().build();
    }
}
