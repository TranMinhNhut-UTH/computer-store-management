# Authorization Foundation Part 3A

**Category Integration V1 local, base ce5019d:** riêng 5 Category method/path đã chuyển từ denyAll sang authenticated boundary; CategoryService vẫn gọi PermissionPolicy/actor/server mapping cho từng operation. Không có role bypass hoặc grants tự động. Health/auth/session/CSRF và denyAll của mọi business route khác giữ nguyên. Các mô tả “mọi business API denyAll” bên dưới là snapshot trước slice này; xem [Category API](category-management-api.md) và rollout mục 13. Không tự approve các policy khác hoặc cho phép thao tác DB.

Ngày 2026-11-21; Part 3A trên `feature/authorization`, base `cf72b05` (Foundation PR #1 đã merge main), đã commit/push `2e152a3`, chưa review/merge Part 3A. Part 3B-2A hiện là implementation local của catalog/policy/tests được mô tả ở cuối tài liệu; không có CRUD/endpoint mới, seed hoặc permission matrix được tự phê duyệt.

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

## API/session policy tại checkpoint Part 3A (lịch sử)

Public: GET /api/health, GET /api/v1/auth/csrf, POST /api/v1/auth/login (vẫn yêu cầu CSRF). GET /api/v1/auth/me yêu cầu authenticated; POST /api/v1/auth/logout yêu cầu authenticated + CSRF. Các endpoint nghiệp vụ vẫn denyAll, kể cả khi một guard primitive có thể đạt. Không thêm method-security/endpoint mapping, JWT, CORS wildcard hoặc đổi cookie/session production policy.

Login/Logout/CSRF và response /me không đổi. /me vẫn dùng snapshot principal theo implementation trước; kiểm tra active mới chỉ áp dụng ở các lần gọi CurrentActorService/guard mới. **Không khẳng định session cũ đã bị thu hồi**: helper từ chối account inactive khi được gọi nhưng không invalidate session, không thay SecurityContext và không chặn logout hợp lệ. Thu hồi phiên toàn cục và xử lý race giữa kiểm tra active/quyền với nghiệp vụ ghi còn PENDING.

## Kiểm thử và phần còn lại

Isolated tests dùng mock persistence, standalone MVC và context Spring giới hạn cho transactional proxies; không auto-configure Spring Boot/datasource/Flyway. Test JPQL parse dùng mapping Entity thật và Hibernate metadata, tắt JDBC metadata/schema actions, ConnectionProvider từ chối mọi kết nối; không chạy query lên database.

Regression gồm các test UTC/shared error/validation, Security Filter, employee authentication, session Login/Logout/CSRF và manual bootstrap đã có; không chọn ComputerStoreBackendApplicationTests vì đó là full Boot context. Kết quả cụ thể được ghi ở tracker sau khi chạy. Không dùng mock/metadata PASS để chứng minh execution plan, MySQL query count/isolation hoặc session revocation thực tế.

Kết quả 2026-11-21: **126/126 PASS**, 0 failure/error/skipped: 36 tests mới của Part 3A và 90 regression tests. Có kiểm tra proxy read-only và tham gia transaction có sẵn, không khởi động Spring Boot với DB, không gọi MySQL/Flyway hoặc bootstrap thật.

PENDING: phê duyệt permission catalog/role mapping và endpoint/use-case policy (P03), tích hợp từng nghiệp vụ, scope/ownership, thu hồi phiên và đồng bộ quyền khi account đổi trạng thái, kiểm chứng SQL/query plans/concurrency trên MySQL được phép. Part 3B-2A chỉ thêm catalog/policy guard/tests local như dưới đây; chưa mở business API, tạo seed, chạy migration, bootstrap Admin hoặc cấp role/permission runtime.

## Authorization Part 3B-2A — catalog và policy contract local

Ngày 2026-11-21, dựa trên [Permission Matrix V1](authorization-permission-matrix.md); **chưa commit/push**. D01–D13 không tự trở thành APPROVED.

| Thành phần mới | Contract |
|---|---|
| PermissionCode | Enum đúng 60 code, grouping 16 module; code() là tên canonical, fromCode() lookup chính xác, không trim/đổi case hoặc tạo code mới. |
| PermissionCode.Status | 51 ELIGIBLE_PROPOSED: đủ để chuẩn bị kỹ thuật; 9 PROVISIONAL_DENY: luôn giữ DENY qua policy. Cả hai không có nghĩa đã approve, seed hoặc cấp cho role. |
| PermissionPolicy.requirePermission(String) | Code phải do server chọn. Gọi AuthorizationService.requirePermission để xác minh actor và mapping thực tế, sau đó chỉ chấp nhận code có trong catalog và ELIGIBLE_PROPOSED. Trả CurrentActor từ guard cũ khi cả hai điều kiện đạt; không fallback theo role hoặc UI. |

Ví dụ contract cho Service tương lai (không tạo endpoint):

```java
CurrentActor actor = permissionPolicy.requirePermission(PermissionCode.PRODUCT_READ.code());
// Còn phải kiểm tra record scope/projection và mọi business predicates của use case.
```

- Nguồn role/permission không đổi: CurrentActorService → RoleResolutionService → AuthorizationService như Part 3A. Catalog không chứa role-to-permission grants, không đọc quyền từ client, không seed/ghi DB hoặc cache quyền vào session.
- Thứ tự giữ authentication trước: anonymous/inactive vẫn 401 UNAUTHENTICATED, kể cả required code unknown/provisional. Authenticated thiếu mapping, unknown hoặc provisional trả 403 ACCESS_DENIED với detail hiện có. Lỗi hạ tầng không biến thành allow/403; handler 500 sanitized hiện có vẫn xử lý.
- PROVISIONAL_DENY gồm ROLE_READ, PERMISSION_READ, CUSTOMER_DEACTIVATE, ADJUSTMENT_REJECT, ADJUSTMENT_CANCEL, SALE_COMPLETE, PAYMENT_VOID, REFUND_READ, REFUND_CREATE. Có mapping nhầm trong DB vẫn không qua PermissionPolicy; unknown canonical code có mapping cũng bị từ chối.
- AuthorizationService/RoleResolutionService Part 3A **không bị sửa**; raw primitive không thực thi catalog status. Mọi module TV1/TV2 tích hợp permission contract mới phải dùng PermissionPolicy, không gọi raw requirePermission như đường vòng bỏ qua eligibility. requireAnyRole chỉ là AND gate bổ sung khi use-case contract yêu cầu, không role-bypass.
- Policy không mở transaction mới hoặc thay transaction owner. Resolver vẫn dùng proxy read-only/REQUIRED như Part 3A và tham gia transaction của caller nếu có. Guard phải chạy trước mutation; state, ownership, field scope, locks, composite permission và rollback vẫn thuộc Service use case. Policy đơn lẻ không đủ cho return/refund, adjustment self-approval hoặc bảo vệ ADMIN cuối.
- SecurityConfig, login/logout/CSRF, /me và mọi business denyAll giữ nguyên. Chưa có consumer production gọi policy để mở nghiệp vụ, seed permission/role_permission, cấp 51 quyền, thay Bootstrap, tạo ADMIN hoặc triển khai D13 last-active-ADMIN guard.

### Kiểm thử Part 3B-2A

Isolated tests mới: PermissionCodeTest **5**, PermissionPolicyTest **70**, PermissionPolicyMappingTest **19** = **94 PASS**; bao gồm parameterized cases. Dùng real guard/resolver/actor chain với mock repositories/EntityManager, standalone MVC và controllers chỉ tồn tại trong test source. Kiểm tra đủ 60 code, không trùng/typo, grouping/status, 9 mapped provisional vẫn DENY, ADMIN không wildcard/default grants, unknown, server-selected code, mapping thay đổi, active account, 401/403 và lỗi 500 sanitized.

Chạy cùng **126 regression tests cũ** (18 classes UTC/shared/security/auth/bootstrap/authorization), tổng **220/220 PASS**, 0 failure/error/skipped. Maven test dùng danh sách tường minh 21 classes; không chọn ComputerStoreBackendApplicationTests/full Boot context, không datasource/MySQL/Flyway/bootstrap thật. SecurityConfigTest và AuthenticationHttpTest kiểm tra filter/auth/session/CSRF thực tế trong context MVC giới hạn, không phải kiểm thử runtime với DB.

Chạy lại riêng tests mới từ backend:

```powershell
.\mvnw.cmd -o "-Dtest=PermissionCodeTest,PermissionPolicyTest,PermissionPolicyMappingTest" test
```

Permission/role mapping runtime, seed rollout, API guards cụ thể và các scope/transaction/session/race còn PENDING; isolated PASS không chứng minh MySQL execution hoặc contract đã được approve.

## Session recovery for IAM-003

The approved FR-AUTH-01/02/04 boundary requires a valid employee account for protected access. `GET /api/v1/auth/me` now applies the same current-actor check as other protected APIs. A healthy session retains the existing `{userId, username}` DTO. Anonymous sessions and sessions tied to a missing, inactive or mismatched employee receive `401 UNAUTHENTICATED`. Identity still comes from the session principal and refreshed server data.

If a protected request made by the current signed-in SPA session returns `401`, the app clears the workspace and shows the sign-in form on the same URL. The previous request is never replayed. Sign-in still fetches a fresh CSRF token and uses `/me` to confirm identity. A `403` or network error stays in the current view. Late responses from an older session or an aborted request cannot close the current session.

## P01 V1 employee session revocation

The operator explicitly approved this behavior in the P01 implementation brief. On a successful Staff lock or an actual role-set change, the same database transaction advances the employee's `session_version`. Unlock, ordinary employee edits and unchanged role assignments leave the version unchanged. Failed or rolled-back writes leave sessions at their prior version.

Login binds the current version to the detached servlet-session principal. An employee session with an absent or different version, an inactive account, or a missing account receives `401 UNAUTHENTICATED` on protected requests, including `/me` and mutations. The HTTP check runs before CSRF for revoked sessions. A current session still needs CSRF for writes and returns `403` for insufficient grants. The service checks the actor again and constrains role and permission queries to that version, so a stale request cannot use newly granted roles. Existing logout invalidates the current servlet session. The SPA preserves the route, returns to sign-in on a protected `401`, and never replays a rejected mutation.

Revocation takes effect when the Staff transaction commits. Requests that have already passed the relevant authorization checks can finish using the authorization they obtained. Revocation does not cancel running transactions, replace servlet session storage, or set new idle/absolute timeouts or concurrent-session limits. Sessions issued without a version require fresh login.

V12 is an explicit, separately deployed schema prerequisite. All backend instances must read the committed version from the same primary database, even if they keep separate servlet session stores. Follow the [P01 rollout runbook](session-revocation-rollout.md).

## Employee Change Password V1

The operator approved self-service password changes in the Change Password implementation brief as new V1 scope. Any authenticated, active employee can open Account Security and call `POST /api/v1/auth/change-password` with the session cookie, a valid CSRF header and JSON containing only `currentPassword` and `newPassword` strings. The server identifies the employee through the current session. Both inputs follow the existing BCrypt rules, including the 72-byte UTF-8 limit and exact preservation of spaces and Unicode. Confirmation stays in the form.

Success returns `204`. The write serializes with Staff account changes and commits the BCrypt hash, audit timestamp and advanced P01 version together. Old sessions, including the caller, then receive `401` on protected requests. The SPA shows confirmation and requires fresh sign-in. Invalid input or an incorrect current password returns `400`, and a current session with missing or invalid CSRF receives `403`. A rollback preserves the previous hash and sessions. This uses the existing V12 column.
