# Category Management V1 — Backend Part 1

Ngày 2026-11-21. Branch local `feature/category-management`, base `a0718b19a6ba8bd6dfe7b458d37d69cc20e1e569` từ `feature/authorization`.

**Phụ thuộc Authorization Foundation:** sau fetch, origin/main còn ở `cf72b05`, là tổ tiên của ba commit `2e152a3`, `e251d0c`, `a0718b1`. Authorization chưa merge main. Branch này kế thừa foundation để phát triển slice, không merge/rebase hoặc tạo PR vào main; chỉ chuẩn bị tích hợp sau khi foundation có trong nhánh chung.

**Trạng thái:** Controller/Service/DTO và isolated tests đã triển khai local; API contract dưới đây là đề xuất để review. SecurityConfig không đổi, Category vẫn bị HTTP denyAll. Không có migration, seed hoặc runtime grants mới.

## 1. Căn cứ và phạm vi

- [requirements.md](requirements.md) FR-CAT-01–04: xem/tạo/cập nhật/ngừng sử dụng; FR-CAT-05 bảo toàn tham chiếu, không yêu cầu một endpoint hard delete.
- [database-design.md](database-design.md) mục 1.2/3.2 và V1: category có id, code VARCHAR(50) binary UNIQUE uq_category_code, name VARCHAR(120), description VARCHAR(500) NULL, is_active, created_at/updated_at DATETIME(6). FK product.category_id DELETE/UPDATE RESTRICT.
- [backend-conventions.md](backend-conventions.md): DTO, pagination, normalization, UTC, transaction và lỗi chung. Các lựa chọn DTO/route/page trong tài liệu này chỉ áp dụng slice Category đang review, không tự phê duyệt toàn bộ P05/P10/P11.
- [authorization-foundation.md](authorization-foundation.md), [permission matrix](authorization-permission-matrix.md): dùng PermissionPolicy, actor/server mapping; CATEGORY_READ/CREATE/UPDATE/DEACTIVATE đều ELIGIBLE_PROPOSED. Chưa có approval/registration/grants runtime được thực thi.
- Không Product write, stock, Inventory/Sales, Frontend, hard delete, reactivate, PATCH, bulk, public catalog hoặc multi-store. Không sửa Entity/schema hoặc thêm cột actor vào Category vốn không có cột đó.

## 2. API contract đề xuất — hiện chưa được HTTP security cho phép

| Method / path | Service / permission | Kết quả contract |
|---|---|---|
| GET /api/v1/categories | list/search; CATEGORY_READ | 200 CategoryPageResponse |
| GET /api/v1/categories/{id} | detail; CATEGORY_READ | 200 CategoryResponse; missing 404 |
| POST /api/v1/categories | create; CATEGORY_CREATE | 201 CategoryResponse và Location |
| PUT /api/v1/categories/{id} | full information update; CATEGORY_UPDATE | 200 CategoryResponse; không sửa active/createdAt |
| POST /api/v1/categories/{id}/deactivate | deactivate; CATEGORY_DEACTIVATE | 200 CategoryResponse; không hard delete |

Production hiện trả 401 cho anonymous read, 403 cho authenticated requests tới các route trên, kể cả role ADMIN/MANAGER hoặc có authorities giả lập. Mutation thiếu CSRF bị 403. Các status 200/201/400/404/409 của Controller/Service được kiểm tra bằng standalone MVC chỉ trong tests; không phải bằng chứng HTTP live API đã được mở.

List nhận q tùy chọn, active tùy chọn (bỏ qua = cả active/inactive), page mặc định 0, size mặc định 20, giới hạn 1–100; page không âm và offset không vượt Integer.MAX_VALUE. Sort cố định name ASC, id ASC, không nhận sort expression từ client.

q được strip, kiểm tra Unicode/giới hạn 120 code points, blank thành không lọc. Tìm literal contains trên code/name: escape %, _ và !; pattern của code được uppercase vì code canonical và collation binary, name theo collation văn bản schema. active chỉ lọc dữ liệu, không cấp quyền. Behavior/collation/physical query vẫn cần MySQL verification trong task sau.

Path id là decimal string canonical dương trong miền Long: không dấu cộng, số 0 đầu, exponent, số âm/0 hoặc overflow. Không có client-supplied id trong create payload.

## 3. DTO và validation

### Request dùng chung cho create và PUT information update

```json
{
  "code": " cpu ",
  "name": " Processors ",
  "description": null
}
```

CategoryWriteRequest chỉ có code/name/description. Các trường khác, gồm id/active/actorId/createdAt/updatedAt, bị reject 400 qua Jackson any-setter; không data-bind Entity.

- code/name có @NotBlank; Service vẫn kiểm tra lại khi gọi nội bộ. Code dùng IdentifierNormalizer.businessCode: trim ASCII edge whitespace, uppercase ASCII, chỉ A–Z/0–9/dot/underscore/hyphen; sau chuẩn hóa bắt buộc dài 1–50.
- name strip, giữ Unicode/case, không blank, tối đa 120 Unicode code points. description nullable, strip, tối đa 500 code points; empty string và null giữ riêng. Reject malformed Unicode, không cắt dữ liệu cho vừa cột.
- PUT là thay thế bộ thông tin code/name/description: code/name bắt buộc; description omitted hoặc null nghĩa là clear. Không có PATCH omitted/null semantics mới; không thay trạng thái qua DTO information.
- Các giới hạn theo schema được kiểm tra ở Service trước persistence; @NotBlank hỗ trợ MVC field validation. Lỗi 400 dùng Shared ProblemDetail, không echo rejected payload.

### Response

CategoryResponse gồm id (decimal string), code, name, description, active (boolean), createdAt, updatedAt (ISO-8601 UTC có Z). Không Entity, lazy association hoặc Product collection. Không thêm field nghiệp vụ chưa có trong schema.

CategoryPageResponse gồm items, page, size, totalElements, totalPages; page metadata là JSON numbers, không expose PageImpl. List response tạo immutable items snapshot.

## 4. Service rules và transaction

- Mỗi use case gọi PermissionPolicy trước truy cập Category. Code do server chọn; không nhận role/permission/actor từ client, không hardcode Manager hoặc Admin bypass.
- Read methods chạy @Transactional(readOnly=true); create/update/deactivate @Transactional write với REQUIRED mặc định. Guard/resolver tham gia transaction của Service; không REQUIRES_NEW, không transaction ở Controller.
- Create normalize trước duplicate lookup, chỉ tạo Category active=true, dùng UtcTimeProvider cho cả createdAt/updatedAt cùng thời điểm UTC microsecond. ID do persistence sinh; tests gán ID trên mock, không lấy ID client.
- Update khóa Category PESSIMISTIC_WRITE; bảo toàn active/createdAt, cho sửa code/name/description sau validation và kiểm tra UNIQUE loại chính record hiện tại. Có thể sửa thông tin Category inactive nhưng không reactivate.
- Precheck duplicate không thay DB UNIQUE. saveAndFlush cố tình kiểm tra trước khi ra khỏi Service transaction; chỉ nhận diện Hibernate ConstraintViolationException có tên uq_category_code hoặc qualified suffix tương ứng là 409. Lỗi FK/integrity khác hoặc chưa chứng minh constraint giữ 500 sanitized; không nuốt exception rồi commit.
- Category Entity giữ nguyên protected constructor; dùng BeanUtils.instantiateClass theo pattern đã có ở Bootstrap để không đổi Entity. CategoryRepository chỉ thêm pagination/search, lock và bounded reference lookup cần cho Category.

### Deactivate: giới hạn bảo thủ, chưa là rule được duyệt cho Category có Product

FR-CAT-04 nói ngừng sử dụng “khi phù hợp”; FR-CAT-05 nói về physical delete. Schema cho phép Product tham chiếu Category inactive, nhưng không tự xác lập business permission cho việc deactivate một Category đang được dùng.

Part 1 do đó **từ chối 409 nếu có bất kỳ Product tham chiếu**, không phân biệt active/inactive. Đây là deny-by-default tạm thời chờ chốt điều kiện nghiệp vụ, không khẳng định requirement đã cấm mọi tham chiếu.

Khi active và không có tham chiếu: đổi active=false, updatedAt từ UTC helper. Khi đã inactive: 200 authorized no-op, không ghi lại timestamp hoặc đọc Product. Không xóa Category/Product, không cascade và không đổi stock.

CategoryRepository đọc nội bộ Product qua FK trong cùng transaction, giới hạn tối đa một row với PESSIMISTIC_READ sau khóa parent Category PESSIMISTIC_WRITE. Locking read được chọn để không dựa vào consistent snapshot có thể đã hình thành bởi authorization queries. Không serialize Product và không cần PRODUCT_READ chỉ để kiểm tra integrity nội bộ.

**Chưa chứng minh khóa/race vật lý trên MySQL.** Trước khi mở API cần test concurrent Product insert/reassignment và Category deactivate; Product use case tương lai phải kiểm tra Category active theo contract phù hợp. Không sửa Product hoặc tự coi parent lock đã giải quyết mọi workflow liên module trong nhiệm vụ này.

## 5. Authorization và deployment blockers

PermissionPolicy/PermissionCode/CurrentActorService/AuthorizationService, SecurityConfig, login/logout/CSRF, cookie/CORS và runtime mappings đều giữ nguyên. Có Controller không làm route được permit: mọi Category request production còn đi vào anyRequest().denyAll().

Role mapping trong matrix vẫn là đề xuất: Manager bốn quyền, Sales/Warehouse chỉ READ, Admin không quyền Category mặc định. Không seed bốn permission, sáu grants hoặc tạo account. Việc đăng ký catalog, cấp role grants và mở HTTP endpoint là các nhiệm vụ riêng.

Còn cần quyết định/verification:
1. Phê duyệt Category API/DTO/page/record visibility contract trước khi đổi route policy; chưa tự APPROVED D01–D13.
2. Chốt có được deactivate khi Product references hay không; giữ deny bảo thủ đến lúc có căn cứ.
3. Stage A manifest metadata/hash, Stage B exact grants và runner/DB preflight theo [rollout plan](authorization-runtime-permission-rollout.md).
4. Runtime MySQL UNIQUE extraction, query/collation/locking/rollback/concurrency; isolated/mock PASS không thay các bằng chứng này.
5. Tích hợp Authorization Foundation vào main; branch Category phụ thuộc foundation và chưa có PR/merge.

## 6. Kiểm thử

| Class | Tests PASS | Phạm vi |
|---|---|---|
| CategoryServiceTest | 17 | Read/write rules, normalize/limits/Unicode, duplicate/404, any Product reference, no-op, permission separation, 401/403/no ADMIN bypass/no delete |
| CategoryHttpTest | 8 | Standalone DTO/routes/statuses, unknown field rejection, ProblemDetail/UTC/string ID, không delete/PATCH/reactivate |
| CategoryRepositoryTest | 2 | HQL parse với Category/Product metadata thật, JDBC provider từ chối connection; lock declarations |
| CategorySecurityTest | 3 | SecurityConfig thật trong MVC-only context; denyAll kể cả ADMIN/category authorities/CSRF, không gọi Service |
| CategoryTransactionTest | 5 | Proxy read-only/write/REQUIRED/rollback với resource-free manager; không chứng minh physical DB rollback |

35 tests Category + đủ 249 regression = **284/284 PASS**, 0 failure/error/skipped, Maven test/compile PASS. Chọn tường minh 27 classes isolated, loại ComputerStoreBackendApplicationTests/full Boot context. Không datasource thật, MySQL, Flyway hoặc Bootstrap CLI.

Chạy riêng tests Category từ backend:

```powershell
.\mvnw.cmd -o "-Dtest=CategoryServiceTest,CategoryHttpTest,CategoryRepositoryTest,CategorySecurityTest,CategoryTransactionTest" test
```

## 7. File scope và trạng thái

Thêm production source: catalog/controller/CategoryController.java; catalog/service/CategoryService.java; catalog/dto/request/CategoryWriteRequest.java; catalog/dto/response/CategoryResponse.java và CategoryPageResponse.java.

Sửa duy nhất CategoryRepository.java trong source hiện có. Thêm năm test classes trên và CategoryTestData fixture; thêm tài liệu này, cập nhật ghi chú/trạng thái CAT-001 trong tracker. Không sửa Product Entity/Repository, Inventory/Sales, Frontend hoặc Shared/Identity source.

CAT-001 Backend/Tests/Overall TODO → IN_PROGRESS, Frontend TODO; owner TV1 giữ nguyên. Giữ 33 task IDs, 8 dependencies và mọi task/state TV2; không ghi DONE. Chưa commit/push/PR, migration, seed, DB/Flyway/bootstrap hoặc tạo ADMIN. Dừng sau Backend Part 1.
