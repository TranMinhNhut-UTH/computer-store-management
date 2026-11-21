# Authorization Foundation Part 3A

Ngày 2026-11-21; triển khai local trên `feature/authorization`, base `cf72b05` (Foundation PR #1 đã merge main). Part 3A chưa commit/review/merge. Chỉ có contract actor, đọc role/permission và guard dùng chung; không có CRUD, endpoint mới hoặc permission matrix được tự phê duyệt.

## Contract cho các module

Các class nằm trong `identity.authorization`:

| Thành phần | Contract |
|---|---|
| CurrentActorService.requireActor() | Trả CurrentActor(userId Long, username String) từ authenticated EmployeePrincipal; không nhận tham số actor từ request. Kiểm tra lại account tồn tại, active và username khớp trong dữ liệu server. |
| RoleResolutionService.resolveCurrent() | Trả ResolvedAccess gồm actor, tập EmployeeRole và permission codes thực sự được gắn trong DB; snapshot immutable, không chứa Entity/hash/session ID. |
| AuthorizationService.requireAnyRole(EmployeeRole...) | Cho phép nếu ít nhất một role server yêu cầu có trong snapshot; yêu cầu rỗng/null hoặc chứa null bị từ chối. Trả actor đã kiểm tra nếu đạt. |
| AuthorizationService.requirePermission(String) | Chỉ cho phép code canonical đã có mapping DB cho user; null/blank/unknown/unmapped/wildcard bị từ chối. Trả actor đã kiểm tra nếu đạt. |

CurrentActor là giá trị nội bộ, không phải request DTO. Actor chỉ lấy từ SecurityContextHolder: token phải authenticated, không anonymous và principal phải đúng EmployeePrincipal đang enabled. ID phải hợp lệ, username không trống. Generic user principal, JPA Entity, authentication details, authorities giả, body/query/header actor ID hoặc role do client gửi không được dùng làm nguồn danh tính/quyền.

Missing/invalid principal, user đã mất/inactive hoặc username đổi so với snapshot trả lỗi `401 UNAUTHENTICATED` với detail cố định. Không sửa principal/session để thay đổi danh tính âm thầm. Guard thiếu role/permission trả `403 ACCESS_DENIED`; mã/detail giống contract Security đã có. Hai mã được bổ sung vào ErrorCode để GlobalExceptionHandler/ApiProblems hiện có xử lý service errors, không đưa SQL, code quyền yêu cầu hoặc exception internals vào response. Lỗi hạ tầng tiếp tục đi qua lỗi 500 sanitized, không biến thành quyền được cấp.

## Nguồn role/permission và transaction

- Chỉ bốn role V1 đã seed: ADMIN, MANAGER, SALES_STAFF, WAREHOUSE_STAFF. Enum EmployeeRole xác định tên hợp lệ, không xác định quyền thao tác. Role thực tế lấy qua UserRole.user → AppUser và UserRole.role → AppRole.code; role không biết/không canonical không cấp quyền.
- Permission lấy từ các liên kết thực: UserRole.role = RolePermission.role, RolePermission.permission → Permission.code. Chỉ xét role V1 thực sự của actor, union/deduplicate mapping của nhiều role. Không đọc hay tin quyền từ principal authorities/Frontend.
- Không có role thì không có quyền. Có ADMIN cũng không ngầm có role khác, wildcard hoặc mọi permission. Có Permission row nhưng chưa gắn qua RolePermission/UserRole cũng không đủ để cấp quyền.
- Permission code dùng đúng quy tắc định danh trong database-design: uppercase ASCII `[A-Z0-9._-]`, tối đa 100 ký tự. Không normalize một code sai thành quyền hợp lệ. Các code FIXTURE_* chỉ xuất hiện trong tests, không phải catalog sản phẩm.
- Mỗi lần resolve/guard đọc lại dữ liệu server; không cache role/permission vào HTTP Session, không sửa EmployeePrincipal hoặc GrantedAuthorities hiện có. Snapshot là dữ liệu đọc trong transaction/persistence context hiện tại, không phải bảo đảm thời gian thực vượt qua isolation/cache của JPA.
- RoleResolutionService khai báo transaction read-only; CurrentActorService tham gia cùng transaction qua Spring bean proxy. Propagation REQUIRED: nếu caller đã có transaction nghiệp vụ thì guard tham gia transaction đó, không mở transaction mới hoặc đổi chính sách readOnly của caller. readOnly là hint cho transaction mới, không phải cơ chế phân quyền. Một findById account và tối đa hai JPQL scalar queries cho roles/permissions, không query theo từng role; không lấy collection lazy ra ngoài transaction hoặc đưa Entity vào snapshot. Không sửa Entity/Repository interfaces hiện có.

Sau khi có policy cụ thể được duyệt, Service của Inventory/Purchasing/Sales có thể lấy actor qua guard chung:

```java
CurrentActor actor = authorization.requirePermission(requiredPermissionFromApprovedServerPolicy);
Long performedBy = actor.userId();
```

`requiredPermissionFromApprovedServerPolicy` phải do code/config policy server đã được duyệt quyết định, không nhận từ request. Dữ liệu nghiệp vụ có thể nhận ID quan hệ, nhưng createdBy/confirmedBy/recordedBy phải lấy từ actor đã kiểm tra. `requireActor()` chỉ xác nhận danh tính/active, không thay authorization. Không tự chọn một role hoặc permission cho thao tác nghiệp vụ từ ví dụ này.

## API/session policy giữ nguyên

Public: GET /api/health, GET /api/v1/auth/csrf, POST /api/v1/auth/login (vẫn yêu cầu CSRF). GET /api/v1/auth/me yêu cầu authenticated; POST /api/v1/auth/logout yêu cầu authenticated + CSRF. Các endpoint nghiệp vụ vẫn denyAll, kể cả khi một guard primitive có thể đạt. Không thêm method-security/endpoint mapping, JWT, CORS wildcard hoặc đổi cookie/session production policy.

Login/Logout/CSRF và response /me không đổi. /me vẫn dùng snapshot principal theo implementation trước; kiểm tra active mới chỉ áp dụng ở các lần gọi CurrentActorService/guard mới. **Không khẳng định session cũ đã bị thu hồi**: helper từ chối account inactive khi được gọi nhưng không invalidate session, không thay SecurityContext và không chặn logout hợp lệ. Thu hồi phiên toàn cục và xử lý race giữa kiểm tra active/quyền với nghiệp vụ ghi còn PENDING.

## Kiểm thử và phần còn lại

Isolated tests dùng mock persistence, standalone MVC và context Spring giới hạn cho transactional proxies; không auto-configure Spring Boot/datasource/Flyway. Test JPQL parse dùng mapping Entity thật và Hibernate metadata, tắt JDBC metadata/schema actions, ConnectionProvider từ chối mọi kết nối; không chạy query lên database.

Regression gồm các test UTC/shared error/validation, Security Filter, employee authentication, session Login/Logout/CSRF và manual bootstrap đã có; không chọn ComputerStoreBackendApplicationTests vì đó là full Boot context. Kết quả cụ thể được ghi ở tracker sau khi chạy. Không dùng mock/metadata PASS để chứng minh execution plan, MySQL query count/isolation hoặc session revocation thực tế.

Kết quả 2026-11-21: **126/126 PASS**, 0 failure/error/skipped: 36 tests mới của Part 3A và 90 regression tests. Có kiểm tra proxy read-only và tham gia transaction có sẵn, không khởi động Spring Boot với DB, không gọi MySQL/Flyway hoặc bootstrap thật.

PENDING: permission catalog và role-permission/endpoint/use-case matrix (P03), cơ chế áp policy cho từng nghiệp vụ, thu hồi phiên và đồng bộ quyền khi account đổi trạng thái, kiểm chứng SQL/query plans/lazy-loading behavior và concurrency trên MySQL được phê duyệt. Chưa mở business API, tạo seed, chạy migration, bootstrap Admin hoặc triển khai Part 3B.
