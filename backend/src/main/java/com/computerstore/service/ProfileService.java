package com.computerstore.service;

import com.computerstore.dto.ProfileDtos.*;
import com.computerstore.dto.UserDto;
import com.computerstore.entity.Address;
import com.computerstore.entity.User;
import com.computerstore.exception.AppException;
import com.computerstore.repository.AddressRepository;
import com.computerstore.repository.RefreshTokenRepository;
import com.computerstore.repository.UserRepository;
import com.computerstore.util.IdentifierUtil;
import com.computerstore.util.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** FR-04: hồ sơ cá nhân, đổi mật khẩu, sổ địa chỉ. */
@Service
@RequiredArgsConstructor
@Transactional
public class ProfileService {

    private final UserRepository users;
    private final AddressRepository addresses;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final CurrentUser currentUser;
    private final AuditService audit;

    private User me() {
        return users.findById(currentUser.requireUserId())
                .orElseThrow(() -> new AppException(401, "Tài khoản không tồn tại."));
    }

    @Transactional(readOnly = true)
    public UserDto get() {
        return UserDto.from(me());
    }

    public UserDto update(UpdateProfileRequest req) {
        User user = me();
        var old = AuditService.fields("fullName", user.getFullName(), "gender", user.getGender(),
                "dateOfBirth", user.getDateOfBirth(), "avatarUrl", user.getAvatarUrl());

        user.setFullName(req.fullName().trim());
        user.setGender(req.gender());
        user.setDateOfBirth(req.dateOfBirth());
        user.setAvatarUrl(req.avatarUrl());

        audit.log("PROFILE_UPDATE", "User", String.valueOf(user.getId()), old,
                AuditService.fields("fullName", user.getFullName(), "gender", user.getGender(),
                        "dateOfBirth", user.getDateOfBirth(), "avatarUrl", user.getAvatarUrl()), null);
        return UserDto.from(user);
    }

    public void changePassword(ChangePasswordRequest req) {
        User user = me();
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new AppException(400, "Mật khẩu hiện tại không đúng.");
        }
        PasswordPolicy.ensureStrong(req.newPassword());
        if (req.currentPassword().equals(req.newPassword())) {
            throw new AppException(400, "Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));

        // Đăng xuất mọi thiết bị
        Instant now = Instant.now();
        refreshTokens.findByUserIdAndRevokedAtIsNull(user.getId()).forEach(t -> t.setRevokedAt(now));
        audit.log("PASSWORD_CHANGE", "User", String.valueOf(user.getId()));
    }

    // ------------------------------------------------------------ Sổ địa chỉ
    @Transactional(readOnly = true)
    public List<AddressDto> listAddresses() {
        return addresses.findByUserIdOrderByDefaultAddressDescIdDesc(currentUser.requireUserId())
                .stream().map(AddressDto::from).toList();
    }

    public AddressDto createAddress(AddressRequest req) {
        User user = me();
        boolean first = !addresses.existsByUserId(user.getId());

        Address a = new Address();
        a.setUser(user);
        apply(a, req);
        a.setDefaultAddress(req.defaultAddress() || first);   // địa chỉ đầu tiên tự là mặc định
        if (a.isDefaultAddress()) clearDefault(user.getId());
        return AddressDto.from(addresses.save(a));
    }

    public AddressDto updateAddress(Long id, AddressRequest req) {
        long uid = currentUser.requireUserId();
        Address a = findOwn(uid, id);
        apply(a, req);
        if (req.defaultAddress() && !a.isDefaultAddress()) {
            clearDefault(uid);
            a.setDefaultAddress(true);
        }
        return AddressDto.from(a);
    }

    public void setDefaultAddress(Long id) {
        long uid = currentUser.requireUserId();
        Address a = findOwn(uid, id);
        clearDefault(uid);
        a.setDefaultAddress(true);
    }

    public void deleteAddress(Long id) {
        long uid = currentUser.requireUserId();
        Address a = findOwn(uid, id);
        boolean wasDefault = a.isDefaultAddress();
        addresses.delete(a);
        addresses.flush();
        if (wasDefault) {
            addresses.findFirstByUserIdOrderByIdDesc(uid).ifPresent(next -> next.setDefaultAddress(true));
        }
    }

    private Address findOwn(long userId, Long id) {
        return addresses.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AppException(404, "Không tìm thấy địa chỉ."));
    }

    private void clearDefault(Long userId) {
        addresses.findByUserIdAndDefaultAddressTrue(userId).forEach(d -> d.setDefaultAddress(false));
        addresses.flush();
    }

    private static void apply(Address a, AddressRequest r) {
        a.setRecipientName(r.recipientName().trim());
        a.setPhoneNumber(IdentifierUtil.normalizePhone(r.phoneNumber()));
        a.setProvince(r.province().trim());
        a.setDistrict(r.district().trim());
        a.setWard(r.ward().trim());
        a.setStreet(r.street().trim());
        a.setLatitude(r.latitude());
        a.setLongitude(r.longitude());
    }
}
