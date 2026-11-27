# Computer Store Management (CSM) V1

CSM là ứng dụng web nội bộ dành cho cửa hàng linh kiện và phụ kiện máy tính. V1 hướng đến mô hình một cửa hàng, một kho, với danh mục, đối tác, tài khoản nhân viên và báo cáo hỗ trợ các luồng nhập hàng, tồn kho và bán hàng.

## Công nghệ và kiến trúc

- **Backend:** Java 21, Spring Boot 4.1.1, Spring MVC, Spring Security và Maven Wrapper. REST API trả JSON, sử dụng DTO và kiểm tra quyền trên server.
- **Frontend:** React 19, Vite 8 và JavaScript/JSX. Giao diện dùng chung các thành phần form, bảng, phân trang và hỗ trợ giao diện sáng/tối.
- **Dữ liệu:** MySQL 8.0, Spring Data JPA/Hibernate cho persistence, JDBC cho truy vấn báo cáo và Flyway cho migration có kiểm soát.

Backend là một ứng dụng triển khai chung, chia module theo nghiệp vụ. Controller nhận yêu cầu HTTP, service xử lý quyền và quy tắc nghiệp vụ, repository truy cập dữ liệu. Frontend giao tiếp qua `/api`. Trong môi trường phát triển, Vite chuyển tiếp các yêu cầu này đến backend để giữ luồng cookie phiên và CSRF cùng origin.

## Chức năng V1

| Đã triển khai trong mã nguồn | Phạm vi |
| --- | --- |
| Xác thực nhân viên | Đăng nhập, đăng xuất, đổi mật khẩu, BCrypt, phiên HTTP và CSRF. Phiên hết hạn sau 30 phút không hoạt động hoặc 8 giờ từ lúc đăng nhập. Khóa tài khoản, thay đổi vai trò hoặc đổi mật khẩu thu hồi các phiên cũ sau khi giao dịch commit. |
| Danh mục và sản phẩm | Tra cứu, tạo, chỉnh sửa và ngừng sử dụng. Thay đổi giá có kiểm tra quyền riêng. Tạo sản phẩm và số dư tồn kho ban đầu bằng 0 diễn ra trong cùng giao dịch. Nhân viên đủ quyền báo cáo MANAGER có thể xem số dư tồn, ngưỡng và trạng thái tồn trong chi tiết sản phẩm. |
| Nhà cung cấp và khách hàng | Tra cứu, tạo và chỉnh sửa hồ sơ. Nhà cung cấp có thao tác ngừng sử dụng. Chi tiết khách hàng hỗ trợ điện thoại, email, địa chỉ và ghi chú tùy chọn theo quyền. |
| Quản lý tài khoản nhân viên | Danh sách, tạo và sửa tài khoản, gán vai trò, khóa và mở khóa. Cần cấu hình bật riêng và các quyền ADMIN đã được áp dụng. |
| Báo cáo | Tổng hợp danh mục, sản phẩm và đối tác, tồn hiện tại, tồn thấp, hết hàng và lịch sử biến động đã ghi nhận. Báo cáo chỉ đọc và yêu cầu vai trò MANAGER cùng các quyền đọc cần thiết. |

Nền tảng kho đã có mô hình số dư, sổ biến động và các thao tác dùng trong giao dịch. Luồng nhập hàng, bán hàng, thanh toán, hoàn tiền, trả hàng, kiểm kê và điều chỉnh tồn vẫn đang phát triển hoặc chờ tích hợp. API kho trực tiếp còn chờ triển khai quyền riêng. Báo cáo doanh thu, số bán và số trả phụ thuộc vào dữ liệu giao dịch của các luồng này. Xem [tracker triển khai](docs/implementation-tracker.md) để theo dõi tiến độ và phụ thuộc.

## Vai trò và quyền

| Vai trò | Phạm vi tiêu biểu theo quyền đã cấp |
| --- | --- |
| `ADMIN` | Quản lý tài khoản nhân viên khi tính năng được bật và có các quyền `EMPLOYEE_*`. |
| `MANAGER` | Quản lý danh mục, sản phẩm và nhà cung cấp, đọc hồ sơ khách hàng, xem báo cáo với đủ quyền đọc. |
| `SALES_STAFF` | Đọc danh mục và sản phẩm, tạo và sửa hồ sơ khách hàng. |
| `WAREHOUSE_STAFF` | Đọc danh mục, sản phẩm và nhà cung cấp. Các quyền của luồng kho phụ thuộc rollout tương ứng. |

Backend kiểm tra quyền cụ thể cho từng thao tác. Giao diện chỉ hiển thị các thao tác phù hợp với quyền do server cung cấp. Vai trò ADMIN không mặc nhiên có quyền nghiệp vụ. Xem [ma trận quyền](docs/authorization-permission-matrix.md).

## Chạy trên máy phát triển

Chuẩn bị JDK 21, Node.js `^22.22.2 || ^24.15.0 || >=26.0.0`, npm và MySQL 8.0 chạy cục bộ trên cổng 3306. Backend dùng Maven Wrapper có sẵn. Cần schema phát triển `computer_store_dev` đã được chuẩn bị đúng phiên bản và một tài khoản nhân viên CSM hiện có để đăng nhập.

Mở hai cửa sổ PowerShell tại thư mục gốc của repository.

**Cửa sổ backend**

```powershell
Set-Location backend
$csmDbSecret = $null
try {
    $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/computer_store_dev'
    $env:DB_USERNAME = Read-Host 'Tên người dùng MySQL local'
    $csmDbSecret = Read-Host 'Mật khẩu MySQL local' -AsSecureString
    if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME) -or $csmDbSecret.Length -eq 0) {
        throw 'Cần nhập thông tin kết nối MySQL local.'
    }
    $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $csmDbSecret).Password
    .\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=8080 --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=validate'
} finally {
    Remove-Item Env:DB_URL, Env:DB_USERNAME, Env:DB_PASSWORD -ErrorAction SilentlyContinue
    if ($csmDbSecret) { $csmDbSecret.Dispose() }
}
```

**Cửa sổ frontend**

```powershell
Set-Location frontend
npm.cmd ci
if ($LASTEXITCODE -ne 0) { throw 'Cài dependency frontend thất bại.' }
npm.cmd run dev
```

Mở <http://localhost:5173> để đăng nhập. Kiểm tra backend tại <http://127.0.0.1:8080/api/health> và proxy tại <http://localhost:5173/api/health>. Cả hai trả HTTP 200 và nội dung `Computer Store API is running`. Dừng mỗi tiến trình bằng `Ctrl+C`. Xem [hướng dẫn chạy local](docs/local-development-guide.md) để xử lý lỗi khởi động.

### Lưu ý cơ sở dữ liệu

- Khi chạy ứng dụng, giữ Flyway ở trạng thái tắt và Hibernate ở `ddl-auto=validate`. Backend hiện tại cần schema đã áp dụng V12 cho cơ chế thu hồi phiên.
- Nếu schema thiếu migration hoặc lịch sử không khớp, dừng khởi động và đối chiếu runbook. Việc áp dụng migration là bước riêng, cần xác minh đích, lịch sử, backup và các điều kiện của runbook trước khi thực thi.
- Giữ thông tin kết nối trong môi trường tiến trình. Dùng CLI riêng khi cần cấp tài khoản phát triển. Xem [runbook V12](docs/session-revocation-rollout.md), [rollout phân quyền](docs/authorization-runtime-permission-rollout.md) và [hướng dẫn cấp ADMIN đầu tiên](docs/local-admin-bootstrap.md).

## Cấu trúc repository

| Đường dẫn | Nội dung |
| --- | --- |
| [`backend/`](backend/) | Spring Boot, cấu hình, mã nguồn theo module và kiểm thử backend. |
| [`frontend/`](frontend/) | React/Vite, với màn hình trong `src/features/`, API client trong `src/api/` và thành phần dùng chung trong `src/components/`. |
| [`docs/`](docs/) | Yêu cầu, thiết kế, hợp đồng API, runbook và tracker. |

Tài liệu chính gồm [phạm vi V1](docs/project-scope.md), [yêu cầu](docs/requirements.md), [thiết kế dữ liệu](docs/database-design.md), [quy ước backend](docs/backend-conventions.md) và [design system frontend](docs/frontend-design-system.md).

## Nhóm phát triển

Hai sinh viên phát triển dự án.

| Thành viên | Phụ trách |
| --- | --- |
| **TV1 — Trần Minh Nhựt** | Foundation, Security, Product, Category, Supplier, Customer, Staff, Reports và tích hợp. |
| **TV2 — Nguyễn Phạm Gia Huy** | Inventory, Purchasing, Sales, Payments, Returns và Stocktake. |
