# Quy ước Backend dùng chung — Computer Store Management V1

> Trạng thái: tài liệu đề xuất để nhóm xét duyệt, ngày 2026-11-17. Việc tạo tài liệu không đồng nghĩa phê duyệt các phương án PROPOSED hoặc PENDING.

## 1. Phạm vi, nguồn và cách đọc

- **BASELINE**: quyết định đã có trong tài liệu nguồn; giữ nguyên khi triển khai.
- **PROPOSED**: giải pháp kỹ thuật đề xuất, chưa được nhóm duyệt; chưa tự triển khai.
- **PENDING**: chi tiết chưa đủ quyết định; cần nhóm thống nhất trước công việc phụ thuộc.

Nguồn quyết định: [project-scope.md](project-scope.md) về phạm vi V1; [requirements.md](requirements.md) về yêu cầu chức năng và phi chức năng; [database-design.md](database-design.md), đặc biệt mục 1.2, 3–7, về schema và quy tắc dữ liệu. Tài liệu này không thay thế hay sửa các nguồn đó. Nếu phát hiện xung đột nghiệp vụ giữa nguồn, báo cáo nhóm trước khi triển khai; không tự chọn phương án mới. Các ghi chú hiện trạng cũ trong tài liệu không chứng minh dependency/source hiện tại.

**BASELINE:** Java 21, Spring Boot 4.1.1, Maven, MySQL 8.0.46, Spring Data JPA/Hibernate, Flyway; modular monolith kết hợp layered architecture; REST/JSON; một cửa hàng, một kho. Không customer account, e-commerce checkout, payment gateway, serial/IMEI hoặc bảo hành.

### Hiện trạng tham chiếu

Persistence foundation được ghi nhận tại commit `80eed8b`: 23 Entity, 23 Repository và migration V1. Source hiện có các module identity, catalog, partners, purchasing, sales, inventory và shared.persistence. POM có JPA, Web MVC, Validation, MySQL JDBC và Flyway; chưa có Spring Security dependency. Chưa có Service, DTO nghiệp vụ, security flow, clock UTC hoặc handler lỗi chung. AbstractEntity chỉ chứa ID; Inventory có @Version; associations dùng LAZY. Datasource URL yêu cầu DB_URL, Hibernate ddl-auto=validate; OSIV chưa cấu hình tường minh.

Theo bối cảnh nhóm cung cấp, frontend là React + Vite, JS/JSX. Không tìm thấy frontend/package.json tại đường dẫn dự kiến trong lần soạn này; không suy ra frontend đã đổi stack. Quy ước JSON cần làm việc an toàn với JavaScript.

## A. Authentication

**BASELINE — quyết định:** theo FR-AUTH-01–06 và NFR-SEC-01–02, nhân viên phải đăng nhập, tài khoản khóa bị từ chối đăng nhập, xác định được người dùng/quyền, hỗ trợ logout và hash mật khẩu; không có customer login.

**PROPOSED — giải pháp:** dùng Spring Security và server-side session cho web nội bộ V1. Session ID nằm trong cookie HttpOnly; đổi session ID sau đăng nhập, hủy session phía server và xóa cookie phù hợp khi logout. Dùng PasswordEncoder phù hợp; không trả password hash hoặc session ID trong DTO/log. Không seed Admin với mật khẩu tĩnh hoặc đưa secret bootstrap vào Git.

Cookie dùng Secure khi chạy HTTPS; SameSite, domain, path và ngoại lệ development phải theo mô hình deployment đã duyệt. Với cookie authentication, giữ bảo vệ CSRF cho request thay đổi trạng thái, gồm login/logout theo flow đã chọn. CORS chỉ cho phép origin cụ thể đã duyệt; không dùng wildcard origin cùng credentials. CORS không thay thế authentication hoặc CSRF.

Đề xuất tài khoản bị khóa mất quyền thực hiện các request được bảo vệ tiếp theo, bằng kiểm tra active/revocation session phù hợp; không chỉ chặn lần login mới. Session hết hạn trả 401 dạng lỗi chung cho SPA thay vì HTML redirect.

**Lý do:** hỗ trợ logout/revocation trực tiếp và không cần thêm cơ chế JWT khi chưa có nhu cầu cụ thể. **Phạm vi:** identity, mọi API được bảo vệ và frontend.

**APPROVED — P01 V1:** Quyết định mới của người vận hành trong task “P01 Session Revocation V1”. Khóa tài khoản hoặc thay đổi tập vai trò làm mất hiệu lực mọi phiên trước đó sau khi transaction commit. Mở khóa không phục hồi phiên. Logout hủy phiên hiện tại và giữ CSRF. Nâng schema bằng V12 theo [runbook](session-revocation-rollout.md). Phê duyệt mới trong task Employee Change Password V1 bổ sung thu hồi mọi phiên sau khi đổi mật khẩu thành công.

**APPROVED — Session Lifecycle Security V1:** Người vận hành phê duyệt riêng thời hạn 30 phút không hoạt động và tối đa 8 giờ sau đăng nhập thành công. Backend thực thi cả hai giới hạn; phiên hết hạn cần đăng nhập lại, không gửi lại request đã bị từ chối. V1 không giới hạn số phiên đồng thời.

**PENDING:** deployment cùng/khác origin; cấu hình cookie Secure/SameSite/domain/path; CSRF token exchange và vòng đời token; allowlist CORS; quy trình bootstrap Admin một lần, ai thực hiện và cách cấp secret an toàn.

## B. Authorization

**BASELINE — quyết định:** bốn vai trò ADMIN, MANAGER, SALES_STAFF, WAREHOUSE_STAFF theo ma trận requirements mục 5. Backend kiểm tra quyền, không tin quyền frontend. Warehouse Staff xác nhận nhập; Manager xem theo ma trận. Người đề xuất không tự duyệt adjustment. Không tự gán ADMIN toàn quyền nghiệp vụ.

**PROPOSED — giải pháp:** authentication ở security filter; kiểm tra quyền use case tại Service và cấu hình endpoint tương ứng. Actor createdBy/confirmedBy/recordedBy/... lấy từ authenticated principal thông qua một thành phần dùng chung, không nhận từ request client. Không hardcode actor ID. Mỗi endpoint/use case có mapping quyền rõ ràng; mặc định từ chối các API chưa được cho phép.

Role và Permission đã có trong schema; không suy ra permission catalog hoặc mapping đã được duyệt chỉ vì bảng tồn tại. Đề xuất lỗi 401/403 từ security layer dùng cùng error contract với MVC.

**Lý do:** tránh bỏ sót kiểm tra khi use case được gọi qua nhiều đường và bảo toàn actor lịch sử. **Phạm vi:** toàn bộ module.

**PENDING:** permission code catalog, role-permission mapping, role-only hay kết hợp permission cho V1; cách cập nhật quyền trong session; mapping endpoint chi tiết và giới hạn quyền xem. Không seed hoặc thêm mã quyền trong tài liệu này.

## C. API contract

**BASELINE — quyết định:** REST/JSON; DTO request/response thay vì expose Entity; lỗi có cấu trúc, status phù hợp và không lộ stack trace; danh sách lớn có pagination (NFR-MNT-02, NFR-ERR-01–02, NFR-PER-01–03). HTTP 403 khi đã xác thực nhưng thiếu quyền đã là yêu cầu NFR-SEC-03; bảng HTTP đề xuất dưới đây không đưa yêu cầu đó ra xét duyệt lại.

**PROPOSED — DTO:** dùng record khi phù hợp; tên Create…Request, Update…Request, …Response, …SummaryResponse. Request nhận ID quan hệ, không nhận Entity. Không cho client tự đặt actor, technical audit fields, trạng thái chuyển tiếp hoặc tổng tiền server tính. Business event time do Service xác định; ngoại lệ nhập thời điểm thực tế chỉ khi use case được duyệt. Response chỉ gồm trường cần thiết; thành công trả DTO trực tiếp, không cần envelope cho mọi endpoint.

**PROPOSED — số trong JSON:** ID, money và quantity đều là **decimal string** trong request/response, kể cả quantityDelta âm hợp lệ. Ví dụ ID "123", amount "1500000", quantity "2", quantityDelta "-2". Không dùng dấu phân cách hàng nghìn, dấu cộng, exponent hoặc phần thập phân; dạng chuẩn không có số 0 đầu thừa, số 0 là "0". Field tùy chọn dùng JSON null khi contract cho phép, không chuỗi "null". Tiền VND nguyên đồng; parser kiểm tra miền BigDecimal(19,0), Long và điều kiện từng field. Java vẫn dùng Long/BigDecimal theo Entity, không đổi schema; ID giới hạn trong miền Long mà ứng dụng hỗ trợ. Pagination metadata vẫn là JSON number với giới hạn rõ ràng. Frontend không ép ID/tiền/số lượng lớn qua Number gây mất chính xác.

**PROPOSED — HTTP và lỗi:**

| Tình huống | HTTP đề xuất |
|---|---|
| Đọc/cập nhật/command có response | 200 |
| Tạo resource | 201, Location khi có URL resource |
| Thành công không có body | 204 |
| JSON sai, kiểu/format sai, request validation | 400 |
| Thiếu/sai/hết hạn xác thực | 401 |
| Đã xác thực nhưng không đủ quyền | 403 |
| Resource không tồn tại | 404 |
| Trùng mã, sai trạng thái, confirm lặp, thiếu tồn, conflict đồng thời | 409 |
| Lỗi ngoài dự kiến | 500 với thông báo an toàn |

Dùng ProblemDetail (application/problem+json) với type/title/status/detail/instance và extension code, fieldErrors, mã đối chiếu log khi có. Business error code ổn định, không phụ thuộc message hoặc tên Java exception. Ví dụ PROPOSED: DOCUMENT_ALREADY_CONFIRMED, INSUFFICIENT_STOCK, DUPLICATE_BUSINESS_KEY, CONCURRENT_MODIFICATION. Không đưa SQL, credentials, rejected password hoặc stack trace vào response. Không biến mọi DataIntegrityViolationException thành DUPLICATE_BUSINESS_KEY; chỉ ánh xạ khi xác định được nguyên nhân.

**PROPOSED — pagination:** page bắt đầu 0; size mặc định 20, tối đa 100; tham số sai trả 400. Response riêng gồm items/page/size/totalElements/totalPages; không để PageImpl trở thành contract công khai. Allowlist sort fields; thêm id làm tie-breaker. Filter/sort mặc định theo use case. Không lấy List không giới hạn để phục vụ API danh sách lớn.

**Đã triển khai trong FND-007:** các API danh sách Category, Product, Supplier, Customer và Staff dùng chung kiểm tra `page >= 0`, `1 <= size <= 100` và offset trong miền `int` mà JPA hỗ trợ. Mỗi API giữ DTO, quyền đọc, sort chính và mã lỗi hiện có. Client kiểm tra `page` và `size` khớp request trước khi hiển thị. Response rỗng cho trang vượt trang cuối vẫn hợp lệ để UI có thể quay lại trang còn dữ liệu. Áp dụng cho năm API đang phát hành. P05/P10/P11 vẫn chờ chốt phạm vi chung.

**Lý do:** tránh mất chính xác số trong JS/JSX, giữ API độc lập persistence và giảm tích hợp lỗi giữa module. **Phạm vi:** mọi REST API và frontend.

**PENDING:** phê duyệt decimal-string contract, error schema/catalog và pagination limits; route/versioning, quy tắc PATCH phân biệt omitted/null, field DTO từng use case. Các mã lỗi trên chưa là catalog chính thức.

**PROPOSED — chi tiết contract cần duyệt:** dùng một route prefix/version chung cho các API nghiệp vụ mới (ứng viên /api/v1); chưa đổi endpoint health hiện hữu. Ưu tiên update DTO rõ ràng; nếu dùng PATCH, omitted nghĩa là giữ giá trị, null chỉ có nghĩa xóa giá trị khi field được phép và contract quy định. Field DTO cụ thể do từng use case xác định. Chưa phê duyệt route prefix hoặc PATCH semantics chỉ bằng việc ghi chúng ở đây.

## D. Validation

**BASELINE — quyết định:** validate đầu vào; business rules và integrity theo design. Chuẩn hóa trước kiểm tra UNIQUE/lưu dữ liệu; trùng sau chuẩn hóa thì từ chối, không ghi đè/gộp.

**PROPOSED — giải pháp:** Jakarta Bean Validation trên request DTO kiểm tra bắt buộc, độ dài, format, nested lines. Sau khi parse decimal string, kiểm tra miền số chính xác và không tự làm tròn tiền. Service kiểm tra quyền, tồn tại FK, trạng thái, active, số lượng/tổng tiền, return/refund aggregate và overflow. DB CHECK/UNIQUE/FK là lớp bảo vệ cuối trước race; lỗi DB phải rollback đúng transaction.

**BASELINE — chuẩn hóa theo database-design mục 1.2:**

| Nhóm | Quy tắc trước kiểm tra trùng và lưu |
|---|---|
| Username | Trim đầu/cuối ASCII; lowercase ASCII; chỉ [a-z0-9._-], không Unicode/dấu/khoảng trắng bên trong |
| app_user.email | Trim đầu/cuối ASCII; email ASCII hợp lệ; lowercase toàn địa chỉ; không khoảng trắng bên trong hoặc Unicode |
| SKU, category/supplier/customer code, role/permission code và mọi mã chứng từ đã liệt kê trong design | Trim đầu/cuối ASCII; uppercase ASCII; chỉ [A-Z0-9._-], không Unicode/dấu/khoảng trắng bên trong |
| Văn bản tự nhiên | Giữ Unicode/dấu/hoa thường; chỉ trim đầu/cuối theo design |

Không áp dụng chính sách UNIQUE email của app_user sang supplier/customer vì design không đặt UNIQUE cho hai bảng đó. Password không đi qua normalization username/email.

**PROPOSED:** khi email tùy chọn là null, bỏ qua existsByEmail(null). Khi cập nhật, loại chính bản ghi hiện tại khỏi kiểm tra trùng. Kiểm tra tồn tại trước không thay thế DB UNIQUE vì có race.

**Lý do:** giữ đúng binary identifier collation và không tạo lỗi trùng giả cho nhiều email NULL. **Phạm vi:** identity, catalog, partners và mã chứng từ mọi module.

**PENDING:** helper normalization chung, định nghĩa cụ thể tập khoảng trắng ASCII được trim để tránh khác nhau giữa implementation; empty email thành null hay lỗi; validation messages và rule theo từng request. Không tự mở rộng allowed characters.

**PROPOSED:** một helper normalization chung để các module không tự diễn giải khác nhau. Với email tùy chọn, ứng viên là blank sau trim chuyển thành null; nonblank vẫn phải hợp lệ. Đây chưa là quyết định về empty/null. Frontend dựa vào error code và field path ổn định; thông báo và constraint riêng do module bổ sung theo baseline.

## E. UTC và audit fields

**BASELINE — quyết định:** DATETIME(6) lưu UTC; mỗi bảng có audit fields riêng theo design mục 3. Không thêm createdAt/updatedAt vào mọi Entity hoặc AbstractEntity. Các event lịch sử và chứng từ đã tác động kho phải được bảo toàn.

**PROPOSED — giải pháp:** cung cấp Clock UTC dùng chung; lấy Instant từ clock rồi chuyển rõ sang LocalDateTime UTC khi lưu. Thống nhất precision microsecond để khớp DATETIME(6); không dùng LocalDateTime.now() phụ thuộc timezone máy. Lấy cùng một thời điểm nghiệp vụ cho các thay đổi thuộc một transition khi phù hợp.

createdAt gán khi tạo và không viết lại; updatedAt gán khi dữ liệu mutable được thay đổi, chỉ ở bảng có cột. UserRole dùng assignedAt; RolePermission chỉ có createdAt; PaymentRefund và InventoryTransaction không thêm updatedAt. Business timestamps confirmedAt/completedAt/cancelledAt/paidAt/voidedAt/refundedAt/occurredAt/startedAt/proposedAt/approvedAt/snapshotAt/countedAt gán tại sự kiện tương ứng, không tự cập nhật hàng loạt bằng callback. Actor/time và nullability phải tuân CHECK/schema.

Response thời gian là ISO-8601 UTC có Z. Chỉ gắn UTC cho LocalDateTime đã biết tuân quy ước UTC; không gắn Z tùy tiện vào giờ địa phương. Ngày lịch nghiệp vụ là vấn đề riêng với timezone lưu trữ.

**Hiện trạng cần lưu ý:** source chưa có clock/callback; seed role trong V1 dùng CURRENT_TIMESTAMP(6), phụ thuộc MySQL session timezone. Chưa chứng minh timestamp runtime/seed hiện hữu là UTC. Không sửa V1 đã chạy để hợp thức hóa vấn đề này.

**Lý do:** tránh lệch thời gian giữa máy, không làm sai thời điểm lịch sử. **Phạm vi:** mọi luồng ghi, response API và bộ lọc thời gian.

**PENDING:** chấp thuận clock/audit mechanism, quy tắc precision; kiểm chứng JDBC/MySQL session UTC khi được phép; timezone ngày nghiệp vụ và cách tính ranh giới 7 ngày lịch return. Chính sách trả trong 7 ngày đã là BASELINE; cách đếm ngày và giờ hết hạn chưa được quyết định ở đây.

**PROPOSED:** chuẩn hóa precision bằng truncate tới microsecond trước persistence để kết quả xác định; nhóm vẫn cần duyệt phương án này. Không thay chính sách 7 ngày lịch thành 168 giờ. Khi chốt ngày nghiệp vụ, ghi rõ timezone, cách tính ngày đầu/cuối và ví dụ tại ranh giới nửa đêm; không tự coi timezone máy chạy là timezone cửa hàng.

## F. Transaction

**BASELINE — quyết định:** confirm document, inventory balance, movement và các thay đổi liên quan phải atomic. Thiếu tồn ở một dòng phải rollback toàn bộ. Payment/refund/return aggregate phải được kiểm tra trong transaction có khóa. Không sửa/xóa event lịch sử.

**PROPOSED — giải pháp:** Service use case public được gọi qua Spring bean sở hữu @Transactional với propagation REQUIRED. Inventory operation và ghi movement tham gia cùng transaction của use case. Không dùng REQUIRES_NEW cho movement hoặc bước bắt buộc phải rollback cùng chứng từ. Không đặt toàn bộ business transaction ở Controller; transaction từng Repository riêng lẻ không đủ.

Business exception đề xuất là unchecked exception để rollback nhất quán; nếu chọn checked exception, phải cấu hình rollback tương ứng. Không bắt lỗi rồi trả thành công hoặc tiếp tục transaction đã thất bại. Tránh self-invocation làm bỏ qua transactional proxy. Read-only transaction cho query khi phù hợp; đây không phải cơ chế phân quyền hay cam kết database không ghi. Không thực hiện side effect ngoài DB trong transaction nếu cần bảo đảm rollback mà chưa có thiết kế riêng.

**Lý do:** giữ các thay đổi liên module nhất quán và không để movement tồn tại sau khi document thất bại. **Phạm vi:** purchase, sales, inventory, stocktake, adjustment, payment, return/refund.

**PENDING:** transaction owner/method contract cho từng use case, các lời gọi liên module, quy ước exception và rollback chi tiết. Không tự chọn isolation level/timeout khác mặc định khi chưa có yêu cầu.

## G. Inventory concurrency

**BASELINE — quyết định:** không sửa trực tiếp tồn kho qua CRUD; mọi thay đổi có movement cùng transaction. Khóa chứng từ trước, payment liên quan theo id tăng dần nếu có, inventory theo product_id tăng dần theo design mục 5. Chặn movement trên SKU đang kiểm kê/adjustment mở theo design; @Version không thay thế điều kiện đủ tồn hoặc row lock.

**PROPOSED — giải pháp:** thống nhất pessimistic write locking cho luồng ghi kho V1. Kiểm tra status, active, đủ tồn và điều kiện khóa kiểm kê sau khi có các khóa phù hợp. Việc sửa lines DRAFT và confirm cần phối hợp cùng khóa header để không thay đổi dữ liệu trong lúc confirm. Một đầu mối inventory phục vụ các module, tham gia transaction của bên gọi. Không lock mọi SKU hoặc toàn kho khi nghiệp vụ chỉ tác động một tập SKU.

Inventory.version do Hibernate quản lý; Service không tự tăng. Cập nhật bằng managed Entity để version được xử lý; không dùng bulk/native update bỏ qua version/persistence context nếu chưa có giải pháp được duyệt. Optimistic locking phát hiện xung đột cập nhật, không tự chống confirm trùng hoặc bảo đảm mọi bước nghiệp vụ đúng một lần.

**Lý do:** giảm overselling/deadlock và tránh mỗi module có một đường ghi tồn riêng. **Phạm vi:** tất cả luồng nhập/xuất/trả/hủy/kiểm kê/điều chỉnh.

**PENDING:** lock order liên module đầy đủ, đặc biệt use case chạm order + return + payment + stocktake/adjustment; loại khóa ở từng query và phạm vi khóa dữ liệu master active; transaction ownership và xử lý inventory row chưa tồn tại. Thứ tự nền tảng trong design không thay thế danh sách lock cụ thể cần nhóm duyệt cho từng workflow. Không tự chốt total order giữa nhiều loại chứng từ trong tài liệu này.

**APPROVED — Product Create V1 (P08/DEP-001):** chủ dự án duyệt tạo đúng một dòng inventory số dư 0 trong cùng transaction tạo Product. Lỗi ở một trong hai lần ghi làm rollback cả transaction. Không ghi movement khi khởi tạo, không lazy-create hay tự sửa dòng thiếu. Mã lỗi cụ thể và các quy tắc P08 khác của luồng chứng từ vẫn chờ chốt.

## H. Idempotency và retry

**BASELINE — quyết định:** confirm chỉ làm biến động kho một lần. Movement gốc có UNIQUE source; reversal có UNIQUE reversal_of_transaction_id. Movement đảo không tái sử dụng source line. Confirm lặp không tạo movement mới.

**PROPOSED — giải pháp:** kiểm tra trạng thái dưới khóa header trước mọi side effect; chỉ trạng thái hợp lệ mới được xử lý. Đề xuất 409 DOCUMENT_ALREADY_CONFIRMED khi confirm lại. Trường hợp đã đi tiếp sang trạng thái khác dùng lỗi trạng thái phù hợp. Client timeout đọc lại document trước khi quyết định thử lại. UNIQUE là bảo vệ cuối, không thay thế status/lock/business validation.

Đây là chống tác động nghiệp vụ lặp, chưa là cam kết replay cùng HTTP response hoặc cơ chế Idempotency-Key tổng quát. Không thêm bảng/cột idempotency hoặc coi hai yêu cầu tạo document mới là cùng một thao tác khi chưa có contract.

**BASELINE:** deadlock/lock timeout rollback toàn transaction và retry có giới hạn từ đầu. **PROPOSED:** retry chỉ lỗi concurrency đã phân loại là phù hợp, mỗi lần trong transaction mới và đọc lại trạng thái; không retry lỗi validation, thiếu tồn, sai quyền hoặc sai trạng thái. Không retry trong transaction đã rollback-only hoặc tự replay thao tác có kết quả commit chưa rõ.

**Lý do:** giữ kho đúng một lần khi có click lặp, mạng timeout hoặc request đồng thời. **Phạm vi:** purchase/sales confirm, return confirm, adjustment approval và các transition làm thay đổi kho.

**PENDING:** số lần retry, backoff, danh sách exception được retry, nơi điều phối, HTTP code cuối khi hết retry và cách xử lý kết quả commit chưa xác định; duyệt hành vi 409. Không chốt con số retry trong tài liệu này.

## I. JPA và package conventions

**BASELINE — quyết định:** giữ modular monolith/layered architecture và schema do Flyway quản lý; không dùng Hibernate create/update. Giữ Entity/Repository và AbstractEntity hiện có khi chưa phát hiện lỗi thực sự. Không thêm cascade delete/orphanRemoval làm mất lịch sử.

**PROPOSED — JPA:** open-in-view=false; association giữ LAZY khi phù hợp. Load dữ liệu cần thiết và map DTO trong Service transaction. Dùng projection/fetch join/entity graph theo use case; không chuyển mọi association sang EAGER hoặc thêm collection inverse chỉ để tiện serialize. Repository trả List không giới hạn không dùng trực tiếp cho API danh sách lớn. Không xem inherited delete/save là API cho phép sửa/xóa event lịch sử.

**PROPOSED — package:** dưới com.computerstore.computer_store_backend, giữ các module identity/catalog/partners/purchasing/sales/inventory. Thêm service, controller, dto.request, dto.response bên trong từng module; mapper chỉ khi cần. Shared chỉ chứa phần dùng chung thực sự như lỗi, clock hoặc contract pagination, không gom business logic mọi module vào shared. Không bắt buộc interface + Impl cho mọi Service. Controller xử lý HTTP/validation và gọi Service; Service sở hữu business rule/transaction; Repository truy cập dữ liệu. Các module gọi contract Service phù hợp và tránh vòng gọi; không dùng Controller làm điểm tích hợp nội bộ.

**Lý do:** giảm lazy load ngoài transaction, giữ JSON độc lập persistence và phân công theo module rõ ràng. **Phạm vi:** toàn backend.

**PENDING:** duyệt OSIV, package của thành phần chung, giao diện Service liên module; fetch/query/DTO cụ thể để từng module thực hiện khi có use case, tuân transaction/lock contract chung.

## 2. Danh sách quyết định cần nhóm xác nhận

| Mã | PENDING | Cần chốt trước |
|---|---|---|
| P01 | V1 được phê duyệt riêng: thu hồi tất cả phiên khi khóa hoặc đổi tập vai trò; mở khóa yêu cầu đăng nhập lại. Timeout và số phiên đồng thời còn PENDING. | IAM-003 / IAM-005 |
| P02 | Cookie deployment, origin/HTTPS, CORS và CSRF exchange cụ thể | Frontend authentication integration |
| P03 | Permission catalog, role mapping, cập nhật quyền trong phiên | API authorization |
| P04 | Admin bootstrap an toàn và người thực hiện | Sử dụng hệ thống có phân quyền |
| P05 | Decimal-string DTO, HTTP/error codes và pagination contract | Chia việc Controller/DTO và frontend |
| P06 | Clock/audit policy, microsecond precision, kiểm chứng UTC runtime | Service ghi dữ liệu |
| P07 | Ngày nghiệp vụ, timezone và ranh giới hạn trả 7 ngày lịch | Return/report date rules |
| P08 | Transaction owner, contract inventory, lock order đầy đủ liên module | Chia việc các use case chứng từ/kho |
| P09 | Retry policy chi tiết và confirm-lặp response | Các transition đồng thời |
| P10 | OSIV và shared package conventions | Service/API integration |
| P11 | Chi tiết normalization còn mơ hồ, empty/null và PATCH semantics | Request implementation |

## 3. Ưu tiên và thứ tự triển khai sau khi được duyệt

1. Chốt các hợp đồng dùng chung ảnh hưởng cả nhóm, ưu tiên Security, API contract, UTC, transaction và lock order. PROPOSED không tự chuyển thành BASELINE sau khi chia việc.
2. Triển khai Security/current actor, error handling, clock và normalization chung theo quyết định đã duyệt.
3. Triển khai transaction/locking inventory contract trước các use case confirm; kiểm tra rollback, confirm đồng thời và tranh cùng tồn khi nhiệm vụ kiểm thử được cho phép.
4. Áp dụng DTO, validation, pagination, OSIV và query theo từng use case; thống nhất với frontend trước tích hợp.
5. Bổ sung tối ưu fetch/logging và tài liệu API khi cần. Không mở rộng V1 bằng cơ chế ngoài yêu cầu.

Các module purchasing/sales/inventory phụ thuộc cùng transaction contract. Contract liên module cần được duyệt trước khi phát triển các use case liên quan để tránh các đường ghi tồn, xử lý lỗi hoặc thời gian không nhất quán.

### Mức ưu tiên quyết định

- **P0 — trước phát triển song song:** authentication/deployment flow, authorization/current actor, API/error/decimal-string/pagination contract, normalization/null semantics, clock/audit contract, transaction owner, inventory operation/row lifecycle, lock order liên module, confirm-lặp semantics, OSIV và cấu trúc package chung.
- **P1 — khi triển khai nền tảng chung:** timeout/cookie/CSRF settings cụ thể theo deployment đã duyệt, encoder, bootstrap Admin, precision helper, kiểm chứng UTC runtime khi được phép, retry parameters/exception classification và kết quả khi hết retry hoặc chưa rõ commit outcome.
- **P2 — theo module:** DTO fields, validation messages, filter/sort, query/fetch/projection và mã lỗi riêng theo use case, trong giới hạn contract P0/P1. Quyền từng endpoint phải được duyệt trước khi mở endpoint.

Mức ưu tiên không thay đổi trạng thái BASELINE/PROPOSED/PENDING. Quyết định P0 có thể chốt nguyên tắc trước, còn thông số P1 chốt trước khi thực thi phần phụ thuộc; không coi bất kỳ phương án nào đã được thông qua chỉ vì được xếp ưu tiên.

## 4. Ranh giới của tài liệu

Chỉ soạn quy ước; không thay schema, migration V1, Entity/Repository, POM hoặc cấu hình ứng dụng. Không xem audit tĩnh hoặc kết quả build/validate lịch sử là bằng chứng runtime hiện tại. Không có credentials trong tài liệu. Các quyết định PENDING phải được nhóm xác nhận trước khi triển khai phần phụ thuộc.
