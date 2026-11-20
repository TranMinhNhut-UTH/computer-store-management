# Local first-ADMIN bootstrap

Trạng thái: implementation và isolated tests; **chưa chạy trên MySQL**. Chỉ thực thi sau phê duyệt riêng. Công cụ này tạo tài khoản để xác thực qua Login API, không triển khai Authorization Part 3 hoặc cấp quyền API nghiệp vụ.

## Cơ chế và điều kiện an toàn

- CLI riêng: `com.computerstore.computer_store_backend.identity.security.LocalAdminBootstrap`. Không phải Spring bean, startup runner hoặc HTTP endpoint. Backend khởi động thường không gọi công cụ này; chạy CLI không có tham số chỉ báo disabled, không mở persistence.
- Opt-in bắt buộc: đúng một tham số `--bootstrap-admin`, terminal tương tác có `System.console()`, nhập xác nhận `computer_store_dev`, username, họ tên và mật khẩu hai lần. Không có mật khẩu/username mặc định hoặc cơ chế đọc secret từ file.
- Dùng đúng factory `SecurityConfig.passwordEncoder()` hiện có, hiện trả `BCryptPasswordEncoder`. Không sửa SecurityConfig hoặc policy session/CSRF.
- Chỉ chấp nhận JDBC MySQL local với host literal `localhost`, `127.0.0.1` hoặc `[::1]`; port tùy chọn hợp lệ, path đúng `/computer_store_dev`. Không chấp nhận multi-host, URL userinfo, fragment, database khác hoặc URL chứa credentials. Cũng kiểm tra `SELECT DATABASE()` trong transaction trước mọi truy cập Repository.
- URL query chỉ cho phép các option có giá trị sau, không lặp key: `serverTimezone=UTC`, `connectionTimeZone=UTC`, `forceConnectionTimeZoneToSession=true`, `useSSL=true/false`, `allowPublicKeyRetrieval=true/false`, `characterEncoding=UTF-8`. Các option khác bị từ chối; không tự nới kiểm tra khi URL bị chặn. Với localhost có thể dùng URL tối thiểu trong ví dụ dưới.
- CLI tạo JPA factory riêng chỉ scan identity entities. `hibernate.hbm2ddl.auto=validate`, không generate DDL. Không khởi tạo Spring Boot application, web server hoặc Flyway; việc dependency Flyway có trong classpath không kích hoạt migration.
- Database/schema và V1 seed role phải tồn tại trước. Công cụ không tạo schema, role hoặc permission. Hibernate chỉ validate phần identity mapping mà CLI sử dụng, không xác nhận toàn bộ schema nghiệp vụ.

## Dữ liệu phải cung cấp

Schema thật yêu cầu `username`, `password_hash`, **`full_name`**, `is_active`, `created_at`, `updated_at`. CLI yêu cầu người vận hành nhập họ tên thực, giữ Unicode, trim hai đầu, tối đa 150 code points, không control characters. Username chuẩn hóa theo `IdentifierNormalizer`: trim ASCII, lowercase ASCII, `[a-z0-9._-]`, 1–100 ký tự.

Password không trim/normalize, không được trống/toàn whitespace hoặc malformed UTF-16, tối đa **72 byte UTF-8** theo giới hạn BCrypt. Đây không phải chính sách độ mạnh mật khẩu hoàn chỉnh; người vận hành chọn mật khẩu mạnh, riêng cho CSM, không dùng lại mật khẩu MySQL. CLI từ chối mật khẩu bằng DB_PASSWORD. Không lưu plaintext; `email`, `phone`, `last_login_at` để NULL vì schema cho phép. Tạo account `is_active=true`; created/updated/assigned dùng cùng thời điểm UTC, truncate microsecond từ UtcTimeProvider.

## Chuẩn bị trước khi được phép chạy

1. Review source/tests và phê duyệt thao tác ghi riêng. Dừng Backend và các công cụ có thể sửa tài khoản trong suốt bootstrap. Xác minh local MySQL/schema bằng thao tác chỉ đọc được cho phép ở lượt vận hành.
2. Kiểm tra database target và role `ADMIN` đã seed, không sửa migration V1 để bổ sung account. Xác nhận không có ADMIN nào, kể cả tài khoản inactive.
3. Dùng terminal local riêng, không bật transcript, screen recording, debug, SQL/bind tracing hoặc JVM diagnostics thu thập secrets. Không truyền Java logging override/agent vào công cụ.
4. Java 21 và Maven phải sẵn sàng. Chuẩn bị classpath từ thư mục `backend` (compile, không chạy application hoặc tests có DB):

```powershell
Set-Location C:\Code\computer-store-management\backend
mvn -DskipTests compile dependency:build-classpath '-Dmdep.outputFile=target/bootstrap-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Compile/classpath preparation failed' }
$bootstrapClasspath = 'target/classes;' + (Get-Content -Raw target/bootstrap-classpath.txt).Trim()
```

File classpath chỉ chứa đường dẫn dependencies trong `target/` đã ignore, không chứa credentials. POM chỉ định mainClass ứng dụng bình thường để việc thêm CLI không thay đổi entry point của Spring Boot Maven plugin.

## Nhập credentials và thực thi thủ công sau phê duyệt

CSM username/họ tên được nhập qua prompt của Java. CSM password và confirmation dùng `Console.readPassword()` nên không echo; **không dùng environment variables cho mật khẩu CSM**, command-line arguments, application properties, pipe/file input hoặc script có literal password. Nếu `System.console()` không có, CLI dừng; mở terminal tương tác thực thay vì bỏ bảo vệ này.

Datasource vẫn dùng DB_URL, DB_USERNAME, DB_PASSWORD như ứng dụng. Nếu đã được cấp qua môi trường process đúng đích, không cần nhập lại. Nếu phải thiết lập DB_PASSWORD, dùng prompt không echo trong terminal riêng (đoạn lệnh chỉ là hướng dẫn, không được thực thi trong task implementation):

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/computer_store_dev'
$env:DB_USERNAME = Read-Host 'MySQL username'
$databaseSecret = Read-Host 'MySQL password' -AsSecureString
$databaseSecretPointer = [IntPtr]::Zero
try {
    $databaseSecretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($databaseSecret)
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($databaseSecretPointer)
} finally {
    if ($databaseSecretPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($databaseSecretPointer)
    }
    $databaseSecret.Dispose()
}
try {
    java -cp $bootstrapClasspath com.computerstore.computer_store_backend.identity.security.LocalAdminBootstrap --bootstrap-admin
    $bootstrapExitCode = $LASTEXITCODE
} finally {
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:DB_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:DB_URL -ErrorAction SilentlyContinue
}
```

Environment variables không phải secret vault: DB_PASSWORD ở dạng plaintext trong process environment, được child process kế thừa và có thể bị đọc bởi process owner/admin, debugger hoặc dump. Prompt tránh literal password trong history nhưng không loại bỏ giới hạn này. Không dùng `setx`, không dump môi trường, không truyền DB_PASSWORD trong command line; đóng terminal riêng khi xong. CLI xóa các char[] CSM password mà nó sở hữu trong finally, nhưng không bảo đảm xóa mọi bản sao nội bộ JVM/encoder hoặc bảo vệ máy đã bị kiểm soát.

## Transaction, duplicate và chạy đồng thời

- Một TransactionTemplate/JpaTransactionManager bao trùm kiểm tra catalog, lấy role, kiểm tra account, insert AppUser và insert UserRole, rồi commit. READ_COMMITTED bảo đảm query kiểm tra ADMIN sau khi đợi khóa nhìn thấy commit trước đó.
- Lấy role qua `AppRoleRepository.findByCode("ADMIN")`, khóa **dòng role hiện có** bằng `PESSIMISTIC_WRITE` trước khi kiểm tra liên kết UserRole. Không thay Entity/Repository hoặc seed thêm role.
- Mọi lần chạy CLI trên cùng DB đều tranh cùng khóa ADMIN, kể cả username khác nhau. Một lần commit thì lần tiếp theo thấy ADMIN và dừng. Bất kỳ liên kết ADMIN nào, kể cả user inactive, đều chặn; không có chế độ phục hồi, đổi password hoặc nâng quyền account trùng username.
- `uq_app_user_username` và `uq_user_role_user_role` trong V1 là lớp bảo vệ cuối trước trùng username/link. Lỗi UNIQUE, lock, flush hay transaction không được tự retry và không được chuyển thành thành công; không đoán mọi lỗi integrity là duplicate.
- Flush user/link vẫn trong cùng transaction, không phải commit riêng. Lỗi trước commit yêu cầu rollback toàn bộ; không để link thất bại mà cố giữ user. Không có transaction riêng cho từng bước.
- Timeout transaction 30 giây; lock hint 5 giây phụ thuộc hỗ trợ Hibernate/MySQL, không cam kết mọi tình huống chờ bị cắt đúng thời hạn đó.
- **Giới hạn concurrency:** mocks chỉ chứng minh thứ tự lock/check/write và gọi commit/rollback, không chứng minh isolation/row lock/rollback vật lý của MySQL. Cần kiểm thử hai process trên MySQL được phê duyệt sau này. InnoDB, unique/FK của V1 và quyền DB phải đúng. Schema cho phép nhiều user giữ role ADMIN; không có UNIQUE “chỉ một ADMIN”. Writer bên ngoài không theo khóa này vẫn có thể tạo ADMIN đồng thời, vì vậy phải dừng các writer khác trong lúc chạy. Không tự thêm constraint/schema để giải quyết ngoài phạm vi.

## Kết quả và kiểm tra sau khi chạy thật

Exit 0 + `Admin bootstrap committed...` chỉ được in sau khi transaction hoàn tất và trả CREATED. CLI tắt framework/SQL logging trong process riêng trước khi mở kết nối; chỉ in thông báo cố định, không SQL/stack trace/input/hash. Nếu logging backend khác Logback hiện có, công cụ dừng an toàn.

Các mã dừng: INVALID_INPUT, UNSAFE_DATABASE_TARGET, MISSING_DATABASE_CREDENTIALS, ADMIN_ROLE_MISSING, ADMIN_ALREADY_EXISTS, USERNAME_EXISTS, FAILED. Không sửa dữ liệu để “vượt qua” lỗi. Nếu lỗi kết nối/commit hoặc process bị ngắt, trạng thái commit có thể chưa xác định: **kiểm tra chỉ đọc trước khi thử lại**, không tự xóa account hoặc reset password. Chỉ khi chưa commit và connection đóng thì transaction chưa hoàn tất được DB rollback; không hứa rollback một transaction đã commit.

Sau phê duyệt và thực thi thật, có thể kiểm tra bằng phiên SQL chỉ đọc đúng schema, không chọn `password_hash`:

```sql
SELECT DATABASE();
SELECT u.id, u.username, u.full_name, u.is_active, r.code
FROM app_user u
JOIN user_role ur ON ur.user_id = u.id
JOIN app_role r ON r.id = ur.role_id
WHERE r.code = 'ADMIN';
```

Chỉ test login thành công bằng account thực qua Login UI/session+CSRF khi được phép chạy Backend. `/me` hiện chỉ trả userId/username; principal chưa được cấp authorities. Gắn role ADMIN trong DB **không** cấp toàn quyền business API; không nới SecurityConfig để kiểm tra.

Không tạo demo account, HTTP bootstrap endpoint, permission catalog, role-permission mapping, không sửa Entity/Repository/migration/schema, không đổi password tài khoản đã có, không chạy Flyway hoặc sửa DB ngoài schema local đã xác nhận. Không dùng DELETE/reset/clean để xử lý bootstrap thất bại.

## Isolated tests

```powershell
mvn '-Dtest=AdminBootstrapServiceTest,AdminBootstrapInputTest,LocalAdminBootstrapTest,SecurityConfigTest,EmployeeAuthenticationTest,AuthenticationHttpTest,ApiProblemsTest,BusinessExceptionTest,GlobalExceptionHandlerTest,IdentifierNormalizerTest,TimeConfigTest,UtcTimeProviderTest' test
```

Không chọn `ComputerStoreBackendApplicationTests` vì đó là full application context. Bộ test mới dùng mocks cho persistence/transaction manager, BCrypt thật, transaction template thật và kiểm tra component scan chỉ đọc metadata. Regression MVC/security tests dùng context giới hạn, không datasource/Flyway. Không dùng isolated PASS để suy ra bootstrap thực, MySQL rollback/concurrency hoặc login thực đã PASS.

Kết quả 2026-11-20: **90 tests PASS**, 0 failures/errors/skipped: 30 tests bootstrap mới và 60 regression security/shared/UTC. Không chạy CLI với datasource thật hoặc application context có DB.
