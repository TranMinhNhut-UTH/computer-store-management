package com.computerstore.service;

import com.computerstore.dto.AuthDtos.*;
import com.computerstore.dto.UserDto;
import com.computerstore.entity.*;
import com.computerstore.exception.AppException;
import com.computerstore.repository.*;
import com.computerstore.security.JwtService;
import com.computerstore.util.HashUtil;
import com.computerstore.util.IdentifierUtil;
import com.computerstore.util.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * FR-01 → FR-03. Lưu ý: noRollbackFor = AppException để các thay đổi như tăng bộ đếm
 * đăng nhập sai / số lần nhập sai OTP vẫn được lưu dù sau đó ném lỗi cho client.
 */
@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = AppException.class)
public class AuthService {

    private static final int OTP_MINUTES = 5;
    private static final int OTP_RESEND_COOLDOWN_SECONDS = 60;
    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final int MAX_FAILED_LOGINS = 5;
    private static final int LOCKOUT_MINUTES = 15;
    private static final int REFRESH_TOKEN_DAYS = 7;

    private final UserRepository users;
    private final RoleRepository roles;
    private final VerificationCodeRepository codes;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final OtpSender otpSender;
    private final AuditService audit;

    private final SecureRandom random = new SecureRandom();

    // ------------------------------------------------------------ FR-01: Đăng ký + OTP
    public void register(RegisterRequest req) {
        boolean hasEmail = StringUtils.hasText(req.email());
        boolean hasPhone = StringUtils.hasText(req.phoneNumber());
        if (!hasEmail && !hasPhone) {
            throw new AppException(400, "Cần nhập email hoặc số điện thoại.");
        }
        String email = hasEmail ? IdentifierUtil.normalizeEmail(req.email()) : null;
        String phone = hasPhone ? IdentifierUtil.normalizePhone(req.phoneNumber()) : null;
        PasswordPolicy.ensureStrong(req.password());

        if (email != null && users.existsByEmail(email)) throw new AppException(409, "Email đã được sử dụng.");
        if (phone != null && users.existsByPhoneNumber(phone)) throw new AppException(409, "Số điện thoại đã được sử dụng.");

        Role role = roles.findByCode(RoleCodes.CUSTOMER)
                .orElseThrow(() -> new AppException(500, "Hệ thống chưa khởi tạo vai trò."));

        User user = new User();
        user.setRole(role);
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setVerified(false);
        users.save(user);

        // Ưu tiên gửi OTP qua email nếu có, ngược lại gửi SMS
        issueOtp(user, VerificationPurpose.REGISTER, email != null ? email : phone, email != null);
        audit.log("USER_REGISTER", "User", String.valueOf(user.getId()), null, null, user.getId());
    }

    public AuthResponse verifyRegistration(VerifyOtpRequest req) {
        User user = findByIdentifier(req.identifier())
                .orElseThrow(() -> new AppException(400, "Mã xác thực không hợp lệ hoặc đã hết hạn."));
        if (user.isVerified()) throw new AppException(400, "Tài khoản đã được xác thực.");

        consumeOtp(user, VerificationPurpose.REGISTER, req.code());
        user.setVerified(true);
        audit.log("USER_VERIFIED", "User", String.valueOf(user.getId()), null, null, user.getId());
        return createSession(user);
    }

    public void resendOtp(IdentifierRequest req) {
        // Không tiết lộ tài khoản có tồn tại hay không
        Optional<User> found = findByIdentifier(req.identifier());
        if (found.isEmpty() || found.get().isVerified()) return;
        IdentifierUtil.Parsed p = IdentifierUtil.parse(req.identifier());
        issueOtp(found.get(), VerificationPurpose.REGISTER, p.value(), p.email());
    }

    // ------------------------------------------------------------ FR-02: Đăng nhập / đăng xuất
    public AuthResponse login(LoginRequest req) {
        User user = findByIdentifier(req.identifier())
                .orElseThrow(() -> new AppException(401, "Thông tin đăng nhập không đúng."));

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AppException(403, "Tài khoản đã bị khóa. Vui lòng liên hệ cửa hàng.");
        }
        Instant now = Instant.now();
        if (user.getLockoutEnd() != null && user.getLockoutEnd().isAfter(now)) {
            long seconds = Duration.between(now, user.getLockoutEnd()).getSeconds();
            throw new AppException(429, "Đăng nhập sai quá nhiều lần. Thử lại sau " + ((seconds + 59) / 60) + " phút.");
        }

        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            if (user.getFailedLoginCount() >= MAX_FAILED_LOGINS) {
                user.setLockoutEnd(now.plus(Duration.ofMinutes(LOCKOUT_MINUTES)));
                user.setFailedLoginCount(0);
            }
            audit.log("LOGIN_FAILED", "User", String.valueOf(user.getId()), null, null, user.getId());
            throw new AppException(401, "Thông tin đăng nhập không đúng.");
        }

        if (!user.isVerified()) {
            throw new AppException(403, "Tài khoản chưa được xác thực. Vui lòng nhập mã OTP đã gửi khi đăng ký.");
        }

        user.setFailedLoginCount(0);
        user.setLockoutEnd(null);
        user.setLastLoginAt(now);
        audit.log("LOGIN", "User", String.valueOf(user.getId()), null, null, user.getId());
        return createSession(user);
    }

    public AuthResponse refresh(RefreshRequest req) {
        RefreshToken token = refreshTokens.findByTokenHash(HashUtil.sha256Hex(req.refreshToken()))
                .orElseThrow(() -> new AppException(401, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."));

        if (token.getRevokedAt() != null || token.getExpiresAt().isBefore(Instant.now())
                || token.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new AppException(401, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
        }
        token.setRevokedAt(Instant.now());          // xoay vòng refresh token
        return createSession(token.getUser());
    }

    public void logout(RefreshRequest req) {
        refreshTokens.findByTokenHash(HashUtil.sha256Hex(req.refreshToken())).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                audit.log("LOGOUT", "User", String.valueOf(token.getUser().getId()), null, null, token.getUser().getId());
            }
        });
    }

    // ------------------------------------------------------------ FR-03: Quên / đặt lại mật khẩu
    public void forgotPassword(IdentifierRequest req) {
        Optional<User> found = findByIdentifier(req.identifier());
        if (found.isEmpty() || found.get().getStatus() != UserStatus.ACTIVE) return;  // luôn trả OK để tránh dò tài khoản

        User user = found.get();
        IdentifierUtil.Parsed p = IdentifierUtil.parse(req.identifier());
        try {
            issueOtp(user, VerificationPurpose.RESET_PASSWORD, p.value(), p.email());
            audit.log("PASSWORD_RESET_REQUESTED", "User", String.valueOf(user.getId()), null, null, user.getId());
        } catch (AppException ex) {
            if (ex.getStatus() != 429) throw ex;   // đang trong thời gian chờ gửi lại: bỏ qua
        }
    }

    public void resetPassword(ResetPasswordRequest req) {
        User user = findByIdentifier(req.identifier())
                .orElseThrow(() -> new AppException(400, "Mã xác thực không hợp lệ hoặc đã hết hạn."));
        PasswordPolicy.ensureStrong(req.newPassword());

        consumeOtp(user, VerificationPurpose.RESET_PASSWORD, req.code());

        user.setPasswordHash(encoder.encode(req.newPassword()));
        user.setVerified(true);                    // đã chứng minh sở hữu email/SĐT
        user.setFailedLoginCount(0);
        user.setLockoutEnd(null);
        revokeAllRefreshTokens(user.getId());
        audit.log("PASSWORD_RESET", "User", String.valueOf(user.getId()), null, null, user.getId());
    }

    // ------------------------------------------------------------ Nội bộ
    private Optional<User> findByIdentifier(String identifier) {
        IdentifierUtil.Parsed p;
        try {
            p = IdentifierUtil.parse(identifier);
        } catch (AppException e) {
            return Optional.empty();
        }
        return p.email() ? users.findByEmail(p.value()) : users.findByPhoneNumber(p.value());
    }

    private void issueOtp(User user, VerificationPurpose purpose, String target, boolean isEmail) {
        Instant now = Instant.now();
        Optional<VerificationCode> last = codes.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose);
        if (last.isPresent() && last.get().getCreatedAt().isAfter(now.minusSeconds(OTP_RESEND_COOLDOWN_SECONDS))) {
            throw new AppException(429, "Vui lòng đợi " + OTP_RESEND_COOLDOWN_SECONDS + " giây trước khi yêu cầu mã mới.");
        }
        codes.findByUserIdAndPurposeAndUsedAtIsNull(user.getId(), purpose).forEach(c -> c.setUsedAt(now));

        String code = String.format("%06d", random.nextInt(1_000_000));
        VerificationCode vc = new VerificationCode();
        vc.setUser(user);
        vc.setPurpose(purpose);
        vc.setCodeHash(hashOtp(user.getId(), code));
        vc.setExpiresAt(now.plus(Duration.ofMinutes(OTP_MINUTES)));
        codes.save(vc);

        otpSender.send(target, isEmail, code, purpose);
    }

    private void consumeOtp(User user, VerificationPurpose purpose, String code) {
        VerificationCode record = codes
                .findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(user.getId(), purpose)
                .orElseThrow(() -> new AppException(400, "Mã xác thực không hợp lệ hoặc đã hết hạn."));

        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw new AppException(400, "Mã xác thực không hợp lệ hoặc đã hết hạn.");
        }
        if (record.getAttempts() >= OTP_MAX_ATTEMPTS) {
            throw new AppException(429, "Nhập sai quá nhiều lần. Vui lòng yêu cầu mã mới.");
        }
        if (!HashUtil.constantTimeEquals(record.getCodeHash(), hashOtp(user.getId(), code))) {
            record.setAttempts(record.getAttempts() + 1);
            throw new AppException(400, "Mã xác thực không đúng.");
        }
        record.setUsedAt(Instant.now());
    }

    private static String hashOtp(Long userId, String code) {
        return HashUtil.sha256Hex(userId + ":" + code);
    }

    private AuthResponse createSession(User user) {
        JwtService.AccessToken access = jwt.createAccessToken(user);

        byte[] raw = new byte[64];
        random.nextBytes(raw);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(HashUtil.sha256Hex(refresh));
        rt.setExpiresAt(Instant.now().plus(Duration.ofDays(REFRESH_TOKEN_DAYS)));
        refreshTokens.save(rt);

        return new AuthResponse(access.token(), refresh, access.expiresAt(), UserDto.from(user));
    }

    private void revokeAllRefreshTokens(Long userId) {
        Instant now = Instant.now();
        refreshTokens.findByUserIdAndRevokedAtIsNull(userId).forEach(t -> t.setRevokedAt(now));
    }
}
