package com.computerstore.config;

import com.computerstore.entity.Role;
import com.computerstore.entity.RoleCodes;
import com.computerstore.entity.User;
import com.computerstore.repository.RoleRepository;
import com.computerstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** Khởi tạo 6 vai trò và (tuỳ chọn) tài khoản Quản lý mặc định khi ứng dụng chạy. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roles;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Value("${app.seed.admin-email:}")
    private String adminEmail;

    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        List<Role> defaults = List.of(
                new Role(RoleCodes.MANAGER, "Quản lý", "Toàn quyền hệ thống, duyệt giảm giá, xem báo cáo"),
                new Role(RoleCodes.RECEPTIONIST, "Lễ tân", "Tiếp nhận đơn, tư vấn, tạo đơn tại quầy"),
                new Role(RoleCodes.TECHNICIAN, "Thợ kỹ thuật", "Xử lý công việc lắp đặt, sửa chữa được giao"),
                new Role(RoleCodes.ACCOUNTANT, "Kế toán", "Hóa đơn, thuế, công nợ, đối soát"),
                new Role(RoleCodes.WAREHOUSE, "Thủ kho", "Nhập xuất kho, kiểm kê, serial"),
                new Role(RoleCodes.CUSTOMER, "Khách hàng", "Mua hàng, đặt dịch vụ, đánh giá"));
        for (Role r : defaults) {
            if (!roles.existsByCode(r.getCode())) roles.save(r);
        }

        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) return;
        if (users.existsByRoleCode(RoleCodes.MANAGER)) return;

        User admin = new User();
        admin.setRole(roles.findByCode(RoleCodes.MANAGER).orElseThrow());
        admin.setFullName("Quản trị hệ thống");
        admin.setEmail(adminEmail.trim().toLowerCase());
        admin.setPasswordHash(encoder.encode(adminPassword));
        admin.setVerified(true);
        users.save(admin);
        log.warn("Đã tạo tài khoản Quản lý mặc định: {}. Hãy đổi mật khẩu sau khi đăng nhập.", adminEmail);
    }
}
