package com.computerstore.controller;

import com.computerstore.dto.AuthDtos.*;
import com.computerstore.dto.MessageResponse;
import com.computerstore.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;

    /** FR-01 – Đăng ký bằng email hoặc SĐT; hệ thống gửi OTP xác thực. */
    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest req) {
        auth.register(req);
        return ResponseEntity.accepted().body(
                new MessageResponse("Đăng ký thành công. Vui lòng nhập mã OTP đã được gửi để xác thực tài khoản."));
    }

    /** FR-01 – Xác thực OTP, trả về phiên đăng nhập. */
    @PostMapping("/verify-otp")
    public AuthResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        return auth.verifyRegistration(req);
    }

    @PostMapping("/resend-otp")
    public MessageResponse resendOtp(@Valid @RequestBody IdentifierRequest req) {
        auth.resendOtp(req);
        return new MessageResponse("Nếu tài khoản tồn tại và chưa xác thực, mã OTP mới đã được gửi.");
    }

    /** FR-02 – Đăng nhập bằng email hoặc SĐT. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return auth.refresh(req);
    }

    /** FR-02 – Đăng xuất (thu hồi refresh token). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        auth.logout(req);
        return ResponseEntity.noContent().build();
    }

    /** FR-03 – Yêu cầu mã đặt lại mật khẩu. */
    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody IdentifierRequest req) {
        auth.forgotPassword(req);
        return new MessageResponse("Nếu tài khoản tồn tại, mã đặt lại mật khẩu đã được gửi.");
    }

    /** FR-03 – Đặt lại mật khẩu bằng OTP. */
    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        auth.resetPassword(req);
        return new MessageResponse("Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại.");
    }
}
