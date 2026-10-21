# Backend – Computer Store API (Java Spring Boot)

Module hiện có: **3.1 Tài khoản và xác thực (FR-01 → FR-06)**.

**Công nghệ:** Java 17+, Spring Boot 3.3, Spring Security (JWT), Spring Data JPA (Hibernate), MySQL 8, Lombok, springdoc-openapi (Swagger), Maven.

## Yêu cầu
- JDK 17 trở lên (`java -version`)
- Maven 3.9+ (`mvn -v`) hoặc dùng IDE (IntelliJ/Eclipse/VS Code)
- MySQL 8 đang chạy (hoặc Docker: `docker run -d -p 3306:3306 -e MYSQL_ALLOW_EMPTY_PASSWORD=yes mysql:8`)
- IDE cần bật Lombok (IntelliJ: Settings → Build → Compiler → Annotation Processors → Enable)

## Chạy lần đầu
```bash
cd backend
cp src/main/resources/application-local.yml src/main/resources/application-local.yml
# mở application-local.yml, điền mật khẩu MySQL, jwt key (>= 32 ký tự), mật khẩu admin

mvn spring-boot:run -Dspring-boot.run.profiles=local
```
- Database `computer_store` và các bảng được tạo tự động (`ddl-auto: update`).
- Swagger: http://localhost:5080/swagger-ui.html
- Tài khoản Quản lý mặc định: `admin@computerstore.local` + mật khẩu `admin-password` bạn đặt.
- Mã OTP trong giai đoạn dev được **in ra console** (`[DEV-OTP]`).
- Có thể đặt bằng biến môi trường thay cho file local: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_KEY`, `ADMIN_PASSWORD`.

## Danh sách API
| FR | Method & URL | Quyền |
|----|--------------|-------|
| 01 | POST `/api/auth/register`, `/api/auth/verify-otp`, `/api/auth/resend-otp` | Công khai |
| 02 | POST `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` | Công khai |
| 03 | POST `/api/auth/forgot-password`, `/api/auth/reset-password` | Công khai |
| 04 | GET/PUT `/api/me`, POST `/api/me/change-password` | Đã đăng nhập |
| 04 | GET/POST `/api/me/addresses`, PUT/DELETE `/api/me/addresses/{id}`, PUT `/api/me/addresses/{id}/default` | Đã đăng nhập |
| 05 | GET/POST `/api/staff`, PUT `/api/staff/{id}`, PATCH `/api/staff/{id}/status`, GET `/api/staff/roles` | QUAN_LY |
| 06 | GET `/api/audit-logs` | QUAN_LY |

Gọi API cần đăng nhập: thêm header `Authorization: Bearer <accessToken>`.

## Ghi chú thiết kế
- Mật khẩu băm BCrypt (cost 12); OTP và refresh token chỉ lưu dạng băm SHA-256.
- Access token 15 phút; refresh token 7 ngày, xoay vòng mỗi lần refresh.
- Khóa tạm 15 phút sau 5 lần đăng nhập sai; OTP hết hạn sau 5 phút, tối đa 5 lần nhập sai, gửi lại cách nhau 60 giây.
- Quên mật khẩu / gửi lại OTP luôn trả kết quả giống nhau để không lộ tài khoản có tồn tại hay không.
- Giới hạn 20 request/phút/IP cho `/api/auth/**` (in-memory; nhiều instance thì dùng Redis).
- Phân quyền theo vai trò bằng `@PreAuthorize("hasRole('QUAN_LY')")`.
- Lỗi trả về dạng RFC 7807 (`ProblemDetail`), thông báo nằm ở trường `detail`.
- `ConsoleOtpSender` là bản tạm; cần thay bằng email (JavaMailSender/SendGrid) và SMS khi triển khai.
- Đổi sang SQL Server: thay dependency `mysql-connector-j` bằng `mssql-jdbc` và sửa `spring.datasource.url`.
- Chưa làm: đăng nhập mạng xã hội (tùy chọn trong FR-02), đổi email/SĐT trong hồ sơ.
