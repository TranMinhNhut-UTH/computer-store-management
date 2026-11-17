# Implementation Tracker — Computer Store Management V1

## 1. Tổng quan

Tracker phân chia công việc và ghi nhận trạng thái của hai thành viên, dựa trên [project-scope.md](project-scope.md), [requirements.md](requirements.md), [database-design.md](database-design.md), [backend-conventions.md](backend-conventions.md) và source local được kiểm tra ngày 2026-11-17. Không thay đổi yêu cầu, ownership hoặc phê duyệt phương án kỹ thuật bằng việc ghi vào tracker.

Phạm vi: modular monolith kết hợp layered architecture, một cửa hàng và một kho; bốn vai trò nhân viên. Không có e-commerce, customer account, multiwarehouse, payment gateway, serial/IMEI hoặc warranty.

Hiện trạng có bằng chứng:

- `backend/pom.xml`: Java 21, Spring Boot 4.1.1, Maven, JPA, Validation, Web MVC, MySQL JDBC và Flyway. Chưa có dependency Spring Security.
- Migration `backend/src/main/resources/db/migration/V1__create_initial_schema.sql`, 23 Entity và 23 Repository đã có; persistence foundation được ghi nhận DONE tại `80eed8b`. Audit tĩnh 5 bước theo bàn giao; không coi đó là xác minh runtime hiện tại.
- `application.properties` yêu cầu `DB_URL`, không fallback URL; Hibernate `ddl-auto=validate`. Không kết nối database để kiểm chứng trong lần lập tracker.
- Tài liệu conventions đã có ở `84eb5ec`; các lựa chọn PROPOSED/PENDING vẫn chưa được duyệt hết.
- Bốn file UTC tồn tại, chưa được Git theo dõi. Báo cáo có sẵn trong `backend/target/surefire-reports/` ghi TimeConfigTest: 1 test và UtcTimeProviderTest: 4 tests, không failure/error/skipped. Đây là kết quả đã lưu, không phải lần chạy mới.
- Backend có health controller và persistence; chưa thấy Service, DTO, API nghiệp vụ hoặc security flow. Entity/Repository không đồng nghĩa chức năng đã hoàn thành.
- Frontend theo bối cảnh là React + Vite, JavaScript/JSX; hiện `frontend/src` chưa có file, không có `frontend/package.json`, chưa có bằng chứng chức năng frontend đã triển khai.

Ưu tiên source/cấu hình thực tế cho hiện trạng stack; các mô tả cũ trong tài liệu về Java 17, Boot 3.3.5, TypeScript hoặc chưa có Flyway không được dùng làm trạng thái triển khai. Không sửa các tài liệu nguồn trong nhiệm vụ này.

Tracker có **33 đầu việc**: 11 nền tảng và 22 chức năng. Tám dependency được theo dõi riêng, không cộng vào số đầu việc.

## 2. Danh sách thành viên và ownership

| Thành viên | Ownership |
|---|---|
| TV1 — Bạn | Shared Foundation: UTC, API Error Handling, ProblemDetail, Validation/Normalization, API/Pagination. Identity & Security: authentication, authorization, session/logout, current actor, user management, role/permission integration. Catalog: category/product. Partners: supplier/customer. Basic Reports. |
| TV2 — Đồng đội | Inventory Foundation: Inventory Operations contract, transaction/locking, movement ledger, concurrency/idempotency. Purchasing: Purchase/PurchaseItem, Draft/Confirm, Stock In. Inventory: balance, history, stocktake, adjustment. Sales: SalesOrder/SalesOrderItem, confirm/Stock Out. Payment/Returns: Payment, PaymentRefund, SalesReturn/SalesReturnItem. |

Mỗi người chịu trách nhiệm Backend, Frontend và Tests của các module được giao. Người cung cấp contract và người tích hợp phối hợp theo mục 6; phối hợp không chuyển ownership. Hai dòng nền tảng đã hoàn thành dưới đây là thành quả có trước tracker; ghi “Đã có” để không suy đoán tác giả hay giao lại công việc đã xong.

## 3. Quy ước trạng thái

| Trạng thái | Ý nghĩa |
|---|---|
| TODO | Chưa triển khai phần việc; có thể có dependency chưa giải quyết. |
| IN_PROGRESS | Đang thực hiện; có source hoặc bằng chứng tiến triển nhưng chưa đủ điều kiện review/hoàn thành. |
| BLOCKED | Đã xác định trở ngại ngăn tiếp tục; phải ghi dependency, bên giải quyết và điều kiện gỡ chặn. |
| IN_REVIEW | Phần việc đã sẵn sàng review, có thay đổi và bằng chứng kiểm tra để đối chiếu. |
| DONE | Đủ Definition of Done, đã review và merge; cần bằng chứng tham chiếu. |
| N/A | Chiều không áp dụng, không phải trạng thái tiến độ; cần lý do. |

Backend, Frontend, Tests được cập nhật riêng; Overall không tự suy ra từ việc có Entity/Repository hay tests pass. Overall chỉ DONE khi tất cả chiều áp dụng hoàn thành và review/merge xong. Dependency chưa duyệt không tự đổi tất cả các việc TODO thành BLOCKED.

Với nền tảng thuần backend, Frontend là N/A. Với tài liệu hoặc phạm vi lịch sử không có frontend/test tự động trong bàn giao, ghi N/A và giải thích; không suy ra đã chạy test. Trạng thái PROPOSED/PENDING là trạng thái quyết định trong conventions, không thay thế trạng thái công việc.

## 4. Shared Foundation Tracker

| ID | Chức năng | Owner | Backend status | Frontend status | Tests status | Overall status | Ghi chú/phụ thuộc |
|---|---|---|---|---|---|---|---|
| FND-001 | Persistence Foundation | Đã có; không giao lại | DONE | N/A | N/A | DONE | `80eed8b`: migration V1, 23 Entity, 23 Repository, datasource yêu cầu DB_URL; audit tĩnh theo bàn giao. Tests N/A trong phạm vi bàn giao nền tảng này, không khẳng định integration tests/runtime đã pass. |
| FND-002 | Backend Conventions — tài liệu | Đã có; không giao lại | N/A | N/A | N/A | DONE | `84eb5ec`; DONE chỉ cho tài liệu, không cho implementation hoặc phê duyệt PROPOSED/PENDING. |
| FND-003 | UTC Clock/Time Helper | TV1 | IN_PROGRESS | N/A | IN_PROGRESS | IN_PROGRESS | Có TimeConfig.java, UtcTimeProvider.java và hai test class trong shared/time. Clock.systemUTC(); now() trả LocalDateTime UTC, truncate microsecond. Báo cáo sẵn có: 5 tests pass; chưa commit/review/merge. DEP-004; P06 còn PENDING. |
| FND-004 | API Error Handling | TV1 | IN_PROGRESS | N/A | IN_PROGRESS | IN_PROGRESS | 2026-11-18: Shared API Foundation phần 1 theo quyết định task: BusinessException, ErrorCode, GlobalExceptionHandler; 400/404/409/500, lỗi 500 sanitized. Bộ tests chọn lọc chung FND-004–006: 18 pass, không DB; chưa commit/review/merge. Security chưa triển khai; DEP-002/003. |
| FND-005 | ProblemDetail contract và response | TV1 | IN_PROGRESS | N/A | IN_PROGRESS | IN_PROGRESS | 2026-11-18: ApiProblems và FieldErrorDetail; application/problem+json, code, fieldErrors ổn định, instance không chứa query; standalone MockMvc tests pass trong bộ 18 tests. Task chấp thuận contract phần 1; chưa review/merge, chưa tích hợp Security/frontend. DEP-003; phần P05 khác giữ nguyên. |
| FND-006 | Validation/Normalization | TV1 | IN_PROGRESS | N/A | IN_PROGRESS | IN_PROGRESS | 2026-11-18: IdentifierNormalizer username/businessCode/userEmail theo database-design mục 1.2; ASCII edge whitespace SP/HT/LF/VT/FF/CR, không Unicode trim. Giữ null/empty riêng; requiredness và cú pháp email @Email thuộc request, không đổi blank email thành null, không kiểm tra UNIQUE. Tests pass trong bộ 18 tests; chưa review/merge. P11 empty/null/PATCH và validation từng request còn mở; DEP-003. |
| FND-007 | API/DTO/Pagination conventions và thành phần dùng chung | TV1 | TODO | N/A | TODO | TODO | DTO độc lập Entity; decimal-string, route, pagination limits, OSIV/package còn đề xuất. DEP-003; P05/P10/P11. |
| FND-008 | Inventory Operations contract | TV2 | TODO | N/A | TODO | TODO | Chốt đầu vào/đầu ra, actor/time, lỗi, transaction owner, row lifecycle; không có CRUD sửa tồn tự do. DEP-001/005/006/007; P08/P10. |
| FND-009 | Transaction/locking | TV2 | TODO | N/A | TODO | TODO | Atomic document + balance + movement; thứ tự khóa theo design và chi tiết liên module cần duyệt. Kiểm tra rollback/thiếu tồn/stocktake race. P08. |
| FND-010 | Inventory movement ledger | TV2 | TODO | N/A | TODO | TODO | Ghi movement bất biến, đúng nguồn; reversal không dùng lại source line, unique chống đảo lặp. Có Entity/Repository, chưa có use case ghi ledger. |
| FND-011 | Concurrency/idempotency | TV2 | TODO | N/A | TODO | TODO | Confirm tác động kho đúng một lần; retry từ transaction mới có giới hạn theo contract được duyệt. Response confirm lặp/retry parameters còn P09. |

Các ID FND-008–011 thuộc Inventory Foundation của TV2, được đặt cùng bảng để thấy nền tảng liên module; không chuyển về Shared Foundation của TV1.

## 5. Functional Module Tracker

| ID | Chức năng | Owner | Backend status | Frontend status | Tests status | Overall status | Ghi chú/phụ thuộc |
|---|---|---|---|---|---|---|---|
| IAM-001 | Authentication | TV1 | IN_PROGRESS | TODO | IN_PROGRESS | IN_PROGRESS | 2026-11-18: Phần 1 đã commit 55f489d. Phần 2A local: BCryptPasswordEncoder mặc định strength 10, EmployeeUserDetailsService tra AppUserRepository.findByUsername sau chuẩn hóa username; EmployeeAuthenticationProvider thay provider tạm. EmployeePrincipal giữ ID/username/enabled, không giữ Entity; hash transient/JsonIgnore, token và principal được erase credentials khi xác thực thành công, authorities rỗng khi P03 còn chờ. Schema chỉ có is_active: false (khóa/inactive) hoặc null bị từ chối bằng DisabledException nội bộ, không tạo locked flag; user thiếu thành BadCredentials qua provider, phản hồi 401 vẫn generic. 11 unit tests mới + 34 regression tests = 45 pass, mocks/không DB. Phần 2A chưa commit/review/merge; chưa có login/logout HTTP, Current Actor hoặc frontend. CSRF exchange, cookie/session/revocation/bootstrap và quyền còn P01/P02/P03/P04; bcrypt cost cần đo theo deployment. DEP-002/003. |
| IAM-002 | Authorization | TV1 | IN_PROGRESS | TODO | IN_PROGRESS | IN_PROGRESS | 2026-11-18: Chỉ GET /api/health public sau khi xác minh nội dung; mọi endpoint khác denyAll, kể cả user giả ADMIN trong test. Handler 403 ACCESS_DENIED dùng ApiProblems, giữ CSRF, không CORS wildcard; tests 401/403/sanitization/CSRF pass trong bộ 11 security tests. Chưa có permission catalog/mapping quyền nghiệp vụ hoặc frontend; chưa commit/review/merge. P03; DEP-002. |
| IAM-003 | Session/Logout | TV1 | TODO | TODO | TODO | TODO | Flow logout, expiry, revoke khi khóa/đổi quyền theo quyết định P01/P02/P03. Session hiện là PROPOSED. |
| IAM-004 | Current Actor | TV1 | TODO | N/A | TODO | TODO | Lấy actor từ principal cho nghiệp vụ/audit, không nhận actor do client tự khai. DEP-002/004. |
| IAM-005 | User Management | TV1 | TODO | TODO | TODO | TODO | Danh sách/tạo/cập nhật/gán vai trò/khóa/mở khóa; giữ tham chiếu lịch sử. Phụ thuộc IAM-001–004/006. |
| IAM-006 | Role/Permission integration | TV1 | TODO | TODO | TODO | TODO | Catalog/mapping quyền chi tiết và cách đồng bộ quyền trong phiên chưa duyệt; UI tích hợp quyền trong phạm vi quản trị V1, không mở rộng scope. P03. |
| CAT-001 | Category Management | TV1 | TODO | TODO | TODO | TODO | CRUD phù hợp, inactive, bảo toàn tham chiếu; FR-CAT-01–05. DEP-002/003/004. |
| CAT-002 | Product Management | TV1 | TODO | TODO | TODO | TODO | SKU/biến thể, giá bán, inactive, MinStockLevel, tìm kiếm/phân trang; FR-PRD-01–08. DEP-001/002/003/004. |
| PAR-001 | Supplier Management | TV1 | TODO | TODO | TODO | TODO | CRUD/inactive, giữ lịch sử phiếu nhập; không áp UNIQUE email từ app_user. DEP-002/003/004. |
| PAR-002 | Customer Management | TV1 | TODO | TODO | TODO | TODO | Hồ sơ giao dịch/tìm kiếm, không có customer login; bảo toàn lịch sử. DEP-002/003/004. |
| PUR-001 | Purchase/PurchaseItem — Draft | TV2 | TODO | TODO | TODO | TODO | Tạo/sửa/xóa dòng/hủy DRAFT, supplier và sản phẩm hợp lệ, snapshot/tiền; danh sách/chi tiết. Phụ thuộc CAT-002, PAR-001, DEP-002/003/004. |
| PUR-002 | Purchase Confirm/Stock In | TV2 | TODO | TODO | TODO | TODO | Warehouse Staff confirm một lần; tăng tồn và PURCHASE_IN atomic, không sửa chứng từ đã confirm. DEP-005. |
| INV-001 | Inventory Balance | TV2 | TODO | TODO | TODO | TODO | Xem tồn hiện tại và cảnh báo quantity <= MinStockLevel; không cho CRUD số dư. DEP-001/002/003. |
| INV-002 | Movement History | TV2 | TODO | TODO | TODO | TODO | Xem/lọc theo sản phẩm/thời gian, truy nguồn; phụ thuộc FND-010, DEP-002/003/004. |
| INV-003 | Stocktake | TV2 | TODO | TODO | TODO | TODO | Snapshot/đếm thực tế/variance và khóa SKU đang kiểm kê theo design; không khóa toàn kho. FND-009/011, DEP-007. |
| INV-004 | Inventory Adjustment | TV2 | TODO | TODO | TODO | TODO | Đề xuất có lý do; Manager duyệt, người đề xuất không tự duyệt; tồn và movement chỉ đổi khi duyệt. DEP-007. |
| SAL-001 | SalesOrder/SalesOrderItem — Draft và tra cứu | TV2 | TODO | TODO | TODO | TODO | Sửa dòng DRAFT, khách hàng tùy chọn, tính tiền hợp lệ, tìm kiếm/lọc; phụ thuộc CAT-002, PAR-002, DEP-002/003/004. |
| SAL-002 | Sales Confirm/Stock Out, Complete/Cancel | TV2 | TODO | TODO | TODO | TODO | Snapshot giá lúc confirm; thiếu một SKU rollback toàn đơn; hoàn tất theo design. Hủy CONFIRMED trước bàn giao phải refund tiền đã thu và đảo đầy đủ SALE_OUT; không hủy COMPLETED. DEP-006/007; PAY-001/002. |
| PAY-001 | Payment | TV2 | TODO | TODO | TODO | TODO | Ghi nhận CASH/BANK_TRANSFER/CARD offline, void theo design; trạng thái thanh toán tính riêng, kiểm tra aggregate dưới khóa. DEP-002/003/004; SAL-002. |
| PAY-002 | PaymentRefund | TV2 | TODO | TODO | TODO | TODO | Liên kết payment gốc, không vượt số đã thu; phối hợp cancel/return atomic, không xóa lịch sử. FND-009/011; DEP-007/008. |
| RET-001 | SalesReturn/SalesReturnItem | TV2 | TODO | TODO | TODO | TODO | Đơn COMPLETED đã trả đủ, 7 ngày lịch; số trả lũy kế không vượt bán; chỉ restock phần bán lại được, refund khớp return. P07; DEP-007/008. |
| RPT-001 | Basic Reports | TV1 | TODO | TODO | TODO | TODO | Doanh thu thuần, số bán/trả, tồn hiện tại/tồn thấp và lịch sử nhập-xuất theo design. Không lợi nhuận; không trừ cash refund lần nữa. DEP-008 và INV-001/002; P07. |

## 6. Dependencies

“Contract cần chốt” bên dưới là nội dung cần thống nhất, không phải một giao diện đã được phê duyệt. Trạng thái ban đầu TODO vì chưa có bằng chứng contract đã hoàn tất; phần UTC đã có source vẫn cần review và xác nhận quyết định.

| ID | Phụ thuộc và đầu việc | Owner cung cấp | Owner tích hợp | Contract cần chốt | Trạng thái | Điều kiện hoàn thành |
|---|---|---|---|---|---|---|
| DEP-001 | Product → Inventory row creation; CAT-002, FND-008, INV-001 | TV2 — inventory contract | TV1 — tạo Product; TV2 — balance | Invariant mỗi product có một inventory; cơ chế tạo row, transaction boundary và lỗi khi thiếu row. Tạo row số dư 0 cùng transaction tạo Product còn PROPOSED, không tự chọn lazy-create. | TODO | Hai phía duyệt lifecycle P08; tích hợp và test atomic product/balance, rollback và tình huống thiếu/trùng row; review/merge. |
| DEP-002 | Security → Business APIs; IAM-001–006 → các module nghiệp vụ | TV1 — identity/security | TV1 — catalog/partners/reports; TV2 — purchasing/inventory/sales/payment/returns | Authentication/deployment, principal/current actor, catalog/mapping quyền endpoint, 401/403, session/revocation và frontend flow; P01–P04. | TODO | Contract được duyệt; principal/quyền được tích hợp ở hai phía; tests từ chối chưa đăng nhập/sai quyền/tài khoản khóa và actor đúng; review/merge. |
| DEP-003 | Shared Error/DTO → Frontend clients; FND-004–007 | TV1 — shared contract | TV1 — frontend module mình; TV2 — backend/frontend module mình | ProblemDetail, error codes/field paths, DTO/số JSON, pagination, normalization/null/PATCH; P05/P10/P11. Không biến decimal-string hoặc pagination limits đề xuất thành mặc định đã chốt. | TODO | Nhóm duyệt contract và ví dụ request/response; client hai phía tích hợp và kiểm tra lỗi/validation/phân trang/độ chính xác số; review/merge. |
| DEP-004 | UTC → Business timestamps; FND-003 → mọi luồng ghi/thời gian API | TV1 — clock/helper | TV1 — module mình; TV2 — chứng từ/kho/payment/return | Clock/audit policy, microsecond, UTC DATETIME(6), biểu diễn API; P06. Ngày nghiệp vụ/ranh giới return/report tách riêng ở P07. | IN_PROGRESS | Review/merge bốn file và tests; duyệt P06, xác minh runtime UTC khi có nhiệm vụ cho phép DB; tích hợp timestamp theo từng event. P07 được chốt trước return/report date rules. |
| DEP-005 | Inventory Operations → Purchase Confirm; FND-008–011 → PUR-002 | TV2 — inventory foundation | TV2 — purchasing | Stock In, source purchase line, kiểm tra trạng thái/active/khóa kiểm kê, actor/time, lỗi và transaction chung; P08/P09. | TODO | Contract duyệt; confirm + balance + ledger atomic; test rollback một dòng lỗi, confirm lặp/đồng thời và khóa SKU; review/merge. |
| DEP-006 | Inventory Operations → Sales Confirm; FND-008–011 → SAL-002 | TV2 — inventory foundation | TV2 — sales | Stock Out nhiều SKU, kiểm tra đủ tồn sau khóa, source order line, transaction/lock order và confirm-lặp semantics; P08/P09. | TODO | Contract duyệt; không âm tồn; tests hai đơn tranh cùng tồn, thiếu một dòng rollback toàn đơn, confirm lặp và snapshot giá; review/merge. |
| DEP-007 | Inventory Operations → Returns/Adjustment và cancellation; FND-008–011 → RET-001, INV-003/004, SAL-002 | TV2 — inventory foundation | TV2 — return/stocktake/adjustment/sales; TV1 cung cấp quyền/actor | RETURN_IN theo restock, adjustment có duyệt, reversal SALE_OUT, khóa SKU stocktake, lock order với order/payment/return; P08/P09, P07 cho return. | TODO | Contract duyệt; tests không movement khi restock=0, không đảo lặp, không tự duyệt, không âm tồn, stocktake race và rollback refund/stock khi cancel/return lỗi; review/merge. |
| DEP-008 | Sales/Payment/Returns → Reports; SAL-001/002, PAY-001/002, RET-001 → RPT-001 | TV2 — sales/payment/returns | TV1 — reports | Query/dữ liệu giao dịch và trạng thái; doanh thu đơn COMPLETED trừ return CONFIRMED, không trừ refund lần nữa; lượng bán/trả, filter thời gian và timezone P07. | TODO | Hai phía duyệt công thức/range/query contract; dữ liệu tích hợp có completed/cancelled/return/refund, đối soát kết quả và ranh giới ngày; review/merge. |

Ưu tiên giải quyết trước:

1. DEP-002/003/004: chốt Security/current actor, error/DTO/normalization và UTC trước API/client hoặc luồng ghi phụ thuộc; tham chiếu P01–P06/P10/P11 trong conventions. Source UTC có sẵn không phải bằng chứng P06 đã được duyệt.
2. DEP-001 và FND-008–011: chốt inventory row lifecycle, Inventory Operations, transaction owner, lock order và idempotency P08/P09 trước tích hợp Product hoặc các use case confirm.
3. DEP-005/006/007: triển khai tích hợp chứng từ/kho theo contract đã duyệt; chốt P07 trước return và bộ lọc ngày báo cáo.
4. DEP-008: chốt dữ liệu/công thức báo cáo giữa hai owner trước nghiệm thu báo cáo; phần tồn kho còn phụ thuộc INV-001/002.

Các chi tiết thuộc cùng owner vẫn cần contract và kiểm tra tích hợp; không bỏ qua vì cùng TV2 thực hiện. Quyết định PENDING chỉ chuyển trạng thái khi có xác nhận của nhóm và bằng chứng tham chiếu.

## 7. Definition of Done

- Phạm vi đầu việc đáp ứng requirement/business rule đã chốt; không mở rộng V1. Lựa chọn PROPOSED/PENDING liên quan đã được nhóm quyết định và ghi bằng chứng trước phần triển khai phụ thuộc.
- Backend có use case/API/query và authorization phù hợp, DTO/validation/error/time theo contract đã duyệt; Entity/Repository riêng lẻ chưa đủ.
- Frontend áp dụng có màn hình/flow tích hợp API, xử lý loading/empty/error/validation và quyền; phần không có UI ghi N/A kèm lý do.
- Tests áp dụng có kết quả và phạm vi rõ ràng: unit, API/integration và frontend theo rủi ro. Luồng kho/tiền phải kiểm tra atomic rollback, đồng thời/idempotency, aggregate và bảo toàn lịch sử; không suy từ unit test sang runtime DB.
- Contract liên module đã tích hợp; dependency còn mở có ảnh hưởng phải được giải quyết. Không để API nghiệp vụ ghi trực tiếp tồn mà bỏ ledger.
- Có review của thành viên còn lại, xử lý phản hồi và merge vào nhánh chung; ghi commit/PR hoặc bằng chứng tương ứng. Tests pass hoặc source local chưa đủ Overall DONE.
- Tài liệu/contract liên quan được cập nhật, không lộ secret; không đưa `.vscode/` vào commit.

FND-001/002 giữ DONE theo commit và phạm vi lịch sử được bàn giao, không phải nghiệm thu chức năng hay xác nhận runtime mới. Nhiệm vụ tạo tracker này không chạy ứng dụng, Maven, Flyway hay DB; các kiểm tra runtime nêu trên là điều kiện của nhiệm vụ triển khai sau khi được phép.

## 8. Quy trình cập nhật trạng thái

1. Owner cập nhật đúng ID, giữ ID ổn định; khi tách việc tạo ID mới và ghi quan hệ với ID cũ, không tái dùng ID cho nội dung khác.
2. Cập nhật riêng Backend/Frontend/Tests/Overall theo bằng chứng. Ghi file, commit/PR, báo cáo kiểm tra và phần còn thiếu trong ghi chú; không tự điền ngày hoàn thành dự kiến hoặc kết quả chưa có.
3. Khi BLOCKED, ghi dependency ID, trở ngại cụ thể, owner giải quyết và điều kiện gỡ chặn. Bên cung cấp và tích hợp cùng cập nhật dependency sau khi contract thay đổi.
4. Đưa IN_REVIEW khi thay đổi đã review được; thành viên còn lại kiểm tra. Chỉ chuyển DONE khi đủ mục 7 và có bằng chứng merge; giữ UTC IN_PROGRESS cho đến khi đủ điều kiện.
5. Với quyết định PENDING, ghi tham chiếu xác nhận nhóm riêng; trạng thái DONE của tài liệu conventions không phê duyệt phương án trong tài liệu.
6. Mỗi lần cập nhật ghi lịch sử ngắn: ngày thực tế, ID bị ảnh hưởng, nội dung và bằng chứng. Ownership giữ theo mục 2; không suy diễn tác giả các commit cũ.

## 9. Lịch sử cập nhật

| Ngày cập nhật | ID/phạm vi | Nội dung | Bằng chứng |
|---|---|---|---|
| 2026-11-17 | Khởi tạo tracker; FND-001–011 và toàn bộ module/dependency | Tiếp nhận bối cảnh; ghi 33 đầu việc theo ownership hai thành viên. Persistence và tài liệu conventions giữ DONE theo phạm vi bàn giao; UTC IN_PROGRESS; các chức năng còn lại TODO. Đây là ngày lập tracker, không phải ngày hoàn thành các việc trước đó. | Bốn tài liệu nguồn; source/config local; `git log` có `80eed8b`, `84eb5ec`; bốn file UTC untracked và báo cáo Surefire có sẵn ghi 5 tests pass. Không chạy lại tests hay truy cập DB. |
