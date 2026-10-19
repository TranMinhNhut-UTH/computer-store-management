# Project Scope

## 1. Tên đề tài

**Website quản lý bán hàng và kho linh kiện & phụ kiện máy tính**

## 2. Bối cảnh và mục tiêu

Dự án xây dựng một hệ thống web nội bộ cho cửa hàng kinh doanh linh kiện và phụ kiện máy tính, nhằm hỗ trợ các nghiệp vụ bán hàng, nhập hàng và quản lý tồn kho trên cùng một hệ thống.

Mục tiêu của phiên bản đầu tiên (V1):

- Quản lý tập trung sản phẩm, danh mục, nhà cung cấp và khách hàng.
- Hỗ trợ nhân viên lập phiếu nhập và cập nhật tồn kho theo nghiệp vụ xác nhận.
- Hỗ trợ nhân viên bán hàng tạo đơn, ghi nhận thanh toán và xử lý trả hàng cơ bản.
- Theo dõi lịch sử biến động tồn kho thay vì sửa số lượng tồn trực tiếp không có dấu vết.
- Phân quyền theo vai trò để giới hạn thao tác của từng nhóm người dùng.
- Cung cấp các báo cáo cơ bản phục vụ quản lý cửa hàng.

## 3. Đối tượng sử dụng

Hệ thống V1 có 4 vai trò chính:

### 3.1. Admin

- Quản lý tài khoản nhân viên.
- Khóa/mở khóa tài khoản.
- Quản lý vai trò và quyền truy cập theo phạm vi hệ thống hỗ trợ.

### 3.2. Manager

- Quản lý sản phẩm, danh mục và nhà cung cấp.
- Thay đổi giá bán sản phẩm.
- Theo dõi tồn kho và cảnh báo tồn thấp.
- Duyệt điều chỉnh tồn kho.
- Xem báo cáo.

### 3.3. Sales Staff

- Quản lý thông tin khách hàng phục vụ bán hàng.
- Tạo và xác nhận đơn bán hàng.
- Ghi nhận thanh toán.
- Xử lý trả hàng theo phạm vi V1.
- Xem thông tin sản phẩm và tồn kho cần thiết cho bán hàng.

### 3.4. Warehouse Staff

- Lập phiếu nhập hàng.
- Xác nhận nhập kho.
- Xem tồn kho và lịch sử giao dịch kho.
- Thực hiện kiểm kê.
- Tạo đề xuất điều chỉnh tồn kho khi có chênh lệch.

## 4. Phạm vi chức năng V1

V1 bao gồm các module sau:

1. Authentication
2. Authorization
3. User Management
4. Product & Category Management
5. Supplier Management
6. Customer Management
7. Purchase / Stock In
8. Inventory Management
9. Sales Order
10. Payment cơ bản
11. Return cơ bản
12. Reports cơ bản

## 5. Quy tắc phạm vi

Các giới hạn sau được chốt cho V1:

- Hệ thống phục vụ **một cửa hàng**.
- Hệ thống quản lý **một kho duy nhất**.
- Khách hàng **không có tài khoản và không đăng nhập** vào hệ thống.
- Hình thức bán chính là **bán trực tiếp tại cửa hàng**.
- Không xây dựng quy trình e-commerce như giỏ hàng, checkout online, giao hàng hoặc theo dõi vận chuyển.
- Không tích hợp cổng thanh toán như VNPay, MoMo hoặc Stripe trong V1.
- Thanh toán chỉ được **ghi nhận nội bộ** trên hệ thống.
- Không quản lý serial number/IMEI theo từng sản phẩm.
- Không quản lý bảo hành trong V1.
- Không hỗ trợ multi-branch.
- Không hỗ trợ multi-warehouse.
- Không tách `Purchase Order` và `Goods Receipt`; V1 sử dụng một nghiệp vụ phiếu nhập hàng thống nhất.
- Không cho phép người dùng sửa trực tiếp số lượng tồn kho mà không tạo dấu vết giao dịch.

## 6. Quy tắc nghiệp vụ cốt lõi đã chốt

### 6.1. Đơn bán hàng

Trạng thái đơn hàng:

```text
DRAFT → CONFIRMED → COMPLETED
   └──────────────→ CANCELLED
```

Quy tắc:

- `DRAFT`: được phép thêm/xóa sản phẩm, thay đổi số lượng và hủy đơn.
- Chỉ khi chuyển sang `CONFIRMED`, hệ thống mới trừ tồn kho.
- Hệ thống không cho xác nhận nếu bất kỳ sản phẩm nào không đủ tồn kho.
- `COMPLETED`: giao dịch đã hoàn tất và không được xóa vật lý.
- Đơn đã làm thay đổi tồn kho không được sửa trực tiếp theo cách làm sai lịch sử giao dịch.
- Trạng thái thanh toán được theo dõi riêng với trạng thái nghiệp vụ của đơn hàng để tránh trộn khái niệm "đã xác nhận đơn" với "đã thanh toán".

### 6.2. Nhập hàng

Trạng thái phiếu nhập:

```text
DRAFT → CONFIRMED
```

Quy tắc:

- `DRAFT`: được phép chỉnh sửa hoặc hủy.
- Chỉ khi `CONFIRMED`, tồn kho mới tăng.
- Một phiếu nhập chỉ được xác nhận một lần.
- Phiếu đã xác nhận không được sửa số lượng trực tiếp.
- Nếu dữ liệu đã xác nhận bị sai, việc sửa tồn phải đi qua nghiệp vụ điều chỉnh/reversal phù hợp để giữ lịch sử.

### 6.3. Tồn kho

- Mỗi sản phẩm có số lượng tồn hiện tại trong kho.
- Mọi thay đổi tồn kho phải sinh ra lịch sử giao dịch kho.
- Warehouse Staff có thể thực hiện kiểm kê và tạo đề xuất điều chỉnh khi số lượng thực tế lệch với hệ thống.
- Mỗi đề xuất điều chỉnh phải có lý do.
- Manager duyệt điều chỉnh trước khi thay đổi tồn kho.
- Sản phẩm có thể khai báo `MinStockLevel`.
- Khi tồn kho nhỏ hơn hoặc bằng ngưỡng tối thiểu, hệ thống phải có khả năng cảnh báo tồn thấp.

## 7. Ngoài phạm vi V1

Các chức năng sau **không nằm trong V1**:

- Website mua hàng cho khách.
- Customer account / customer login.
- Shopping cart và checkout online.
- Giao hàng và theo dõi vận chuyển.
- Payment gateway.
- Multi-branch.
- Multi-warehouse.
- Chuyển kho giữa nhiều kho.
- Serial number / IMEI tracking.
- Warranty management.
- Loyalty points / membership nâng cao.
- Quản lý nhân sự, lương hoặc chấm công.
- Kế toán đầy đủ.
- Purchase Order và Goods Receipt tách thành hai quy trình riêng.

## 8. Hướng phát triển sau V1

Các hướng có thể xem xét cho phiên bản sau:

- Warranty management.
- Serial number tracking.
- Multi-warehouse.
- Multi-branch.
- Online sales integration.
- Payment gateway.
- Delivery integration.
- Dashboard và báo cáo nâng cao.
- Audit log chi tiết hơn.

## 9. Công nghệ

### 9.1. Stack đã thống nhất

- Backend: Java 21 + Spring Boot
- Frontend: React + TypeScript
- Database: Microsoft SQL Server
- ORM dự kiến: Spring Data JPA / Hibernate
- Giao tiếp frontend–backend: REST API, dữ liệu JSON
- Version Control: Git / GitHub

### 9.2. Trạng thái triển khai hiện tại

Backend đã được khởi tạo bằng Spring Boot (xem `backend/pom.xml`). Các dependency hiện có:

- `spring-boot-starter-webmvc` — Web / REST API
- `spring-boot-starter-validation` — Validation

Các dependency sau **chưa được thêm vào `pom.xml`** và cần bổ sung ở bước triển khai:

- Spring Data JPA + Hibernate (ORM)
- Microsoft SQL Server JDBC Driver
- Spring Security (authentication / authorization)
- Công cụ quản lý database migration (ví dụ Flyway) — đề xuất sử dụng, nhưng chưa chốt chính thức

### 9.3. Kiến trúc

Kiến trúc dự kiến cho V1 là **modular monolith** kết hợp **layered architecture**, không sử dụng microservices.

## 10. Tiêu chí kiểm soát scope

Một chức năng mới chỉ nên được đưa vào V1 nếu:

1. Phục vụ trực tiếp một trong ba luồng chính: **nhập hàng, tồn kho, bán hàng**; hoặc
2. Là chức năng hỗ trợ bắt buộc cho các luồng trên như authentication, authorization, danh mục dữ liệu hoặc báo cáo cơ bản; và
3. Không làm thay đổi các giới hạn đã chốt như một cửa hàng, một kho, không e-commerce và không payment gateway.

Nếu không thỏa các điều kiện trên, chức năng đó được xem là ứng viên cho V2 thay vì mở rộng V1 giữa quá trình phát triển.
