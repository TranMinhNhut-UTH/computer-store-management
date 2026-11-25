package com.computerstore.computer_store_backend.identity.controller;

import com.computerstore.computer_store_backend.identity.dto.request.ChangePasswordRequest;
import com.computerstore.computer_store_backend.identity.security.PasswordChangeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PasswordChangeController {
    private final PasswordChangeService passwords;

    public PasswordChangeController(PasswordChangeService passwords) { this.passwords = passwords; }

    @PostMapping("/api/v1/auth/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@RequestBody ChangePasswordRequest input) {
        try (input) {
            passwords.change(input);
        }
    }
}
