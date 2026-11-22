# Category Management V1 — Backend Part 1

Ngày 2026-11-21. Branch local `feature/category-management`, base `a0718b19a6ba8bd6dfe7b458d37d69cc20e1e569` từ `feature/authorization`.

**Phụ thuộc Authorization Foundation:** sau fetch, origin/main còn ở `cf72b05`, là tổ tiên của ba commit `2e152a3`, `e251d0c`, `a0718b1`. Authorization chưa merge main. Branch này kế thừa foundation để phát triển slice, không merge/rebase hoặc tạo PR vào main; chỉ chuẩn bị tích hợp sau khi foundation có trong nhánh chung.

**Hiện tại — 2026-11-22, base 8bf5b6c:** năm routes Category dùng authenticated boundary và Service PermissionPolicy; MANAGER bốn quyền, SALES_STAFF/WAREHOUSE_STAFF chỉ READ, không ADMIN mặc định. Đã có executable source V2/V3 ngoài discovery mặc định và standalone runner opt-in, chưa thực thi migration hoặc ghi permission/grants. 388 isolated tests PASS, E2E/physical MySQL rollback còn pending. Các đoạn Part 1/Integration phía dưới là snapshot; xem rollout mục 14 cho source migration mới.

## 1. Căn cứ và phạm vi

- [requirements.md](requirements.md) FR-CAT-01–04: xem/tạo/cập nhật/ngừng sử dụng; FR-CAT-05 bảo toàn tham chiếu, không yêu cầu một endpoint hard delete.
- [database-design.md](database-design.md) mục 1.2/3.2 và V1: category có id, code VARCHAR(50) binary UNIQUE uq_category_code, name VARCHAR(120), description VARCHAR(500) NULL, is_active, created_at/updated_at DATETIME(6). FK product.category_id DELETE/UPDATE RESTRICT.
- [backend-conventions.md](backend-conventions.md): DTO, pagination, normalization, UTC, transaction và lỗi chung. Các lựa chọn DTO/route/page trong tài liệu này chỉ áp dụng slice Category đang review, không tự phê duyệt toàn bộ P05/P10/P11.
- [authorization-foundation.md](authorization-foundation.md), [permission matrix](authorization-permission-matrix.md): dùng PermissionPolicy, actor/server mapping; CATEGORY_READ/CREATE/UPDATE/DEACTIVATE đều ELIGIBLE_PROPOSED. Chưa có approval/registration/grants runtime được thực thi.
- Không Product write, stock, Inventory/Sales, Frontend, hard delete, reactivate, PATCH, bulk, public catalog hoặc multi-store. Không sửa Entity/schema hoặc thêm cột actor vào Category vốn không có cột đó.

## 2. Category V1 contract — authenticated HTTP boundary, Service permission enforcement

| Method / path | Service / permission | Kết quả contract |
|---|---|---|
| GET /api/v1/categories | list/search; CATEGORY_READ | 200 CategoryPageResponse |
| GET /api/v1/categories/{id} | detail; CATEGORY_READ | 200 CategoryResponse; missing 404 |
| POST /api/v1/categories | create; CATEGORY_CREATE | 201 CategoryResponse và Location |
| PUT /api/v1/categories/{id} | full information update; CATEGORY_UPDATE | 200 CategoryResponse; không sửa active/createdAt |
| POST /api/v1/categories/{id}/deactivate | deactivate; CATEGORY_DEACTIVATE | 200 CategoryResponse; không hard delete |

Anonymous với CSRF hợp lệ khi cần trả 401; authenticated thiếu mapping trả 403; đủ quyền server và business validation thì tới Controller/Service (200/201 hoặc lỗi nghiệp vụ). CSRF chạy trước authorization: mutation thiếu/sai CSRF trả 403, kể cả anonymous. Authorities/role do client gửi không cấp quyền. Boundary mới chỉ được kiểm tra trong MVC-only tests, chưa deploy hoặc kiểm chứng HTTP live/MySQL.

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

PermissionPolicy/PermissionCode/CurrentActorService/AuthorizationService, login/logout/CSRF, cookie/CORS và runtime mappings giữ nguyên. SecurityConfig chỉ thay đúng năm Category method/path sang authenticated; mọi business route khác vẫn anyRequest().denyAll().

Policy slice Category đã được người dùng chọn: Manager bốn quyền, Sales/Warehouse chỉ READ, Admin không quyền Category mặc định. CategoryPermissionRollout chứa đúng bốn catalog entries và sáu grant pairs để chuẩn bị rollout, không tự cấp quyền runtime. Không seed hoặc tạo account; mọi thay đổi DB cần phê duyệt thực thi riêng. Không suy rộng approval sang các mục khác của matrix.

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

## 8. Integration V1 — kiểm chứng và E2E MySQL còn pending

SecurityConfig chỉ cho authenticated qua đúng 5 method/path ở mục 2; không `permitAll`
hoặc role matcher cho Category. CurrentActorService vẫn kiểm tra EmployeePrincipal và
user active trên server, RoleResolutionService đọc mappings, PermissionPolicy kiểm tra
eligibility trước Category persistence. Các quyền không tự bao hàm nhau. Chưa deploy;
DB không có grants phù hợp vẫn trả 403. Deactivate giữ 409 khi có Product references;
không Product write, hard delete hoặc reactivate.

CategorySecurityTest dùng filter/controller/service/actor/resolver/policy thật trong
MVC-only context, chỉ mock repositories/EntityManager. Kiểm tra 5 routes, từng grant,
role read-only, anonymous/missing grant/ADMIN/forged principal/provisional, CSRF và mọi
route ngoài scope vẫn deny. CategoryTransactionTest và CategoryRepositoryTest giữ kiểm
tra proxy rollback/REQUIRED và lock/query declarations; không chứng minh physical locks.
CategoryPermissionRolloutTest kiểm tra exact A/B, metadata/hash/immutability, loại mọi
code ngoài Category và không có migration/DB bean. Không đổi Frontend vì DTO/CSRF contract
không thay đổi; các banner trạng thái rollout hiện có cần review khi vận hành thực tế.

Kết quả isolated: **356/356 PASS**, 0 failure/error/skipped trong 28 classes chọn rõ;
281 tests giữ nguyên + 14 CategorySecurityTest thay cho 3 denyAll tests cũ + 61 manifest
tests mới. Bao gồm rollback/locking declaration regression, auth/login/logout/CSRF và
9 provisional deny cases; không chọn ComputerStoreBackendApplicationTests/full Boot.

E2E chỉ chạy trong nhiệm vụ DB được phép riêng, theo thứ tự:

1. Xác minh local computer_store_dev, credentials qua môi trường an toàn, schema/history,
   inventory/grants/backup/window; dừng nếu target/config không xác minh được. Không in
   URL chứa secret/password/hash. Chốt runner/version/checksum trước khi áp A rồi B theo
   [rollout mục 13](authorization-runtime-permission-rollout.md#13-category-v1--exact-payload-đã-chọn-execution-chưa-được-phép).
2. Sau A/B kiểm tra exact rows/pairs và outside-scope snapshots; không tạo tài khoản hay
   assign user_role ngầm. Cần tài khoản thật được cung cấp/ủy quyền riêng cho MANAGER,
   SALES_STAFF, WAREHOUSE_STAFF và ADMIN không Category grant; thiếu thì báo blocked.
3. Startup có kiểm soát: Flyway disabled, Hibernate validate, bind loopback; Vite proxy
   session-cookie cùng origin. GET csrf, login bằng credentials được cấp, xác minh me.
   Anonymous 401, thiếu/sai CSRF 403, thiếu grant 403; đủ grant tới use case.
4. Manager list/detail/create/PUT/deactivate; Sales/Warehouse chỉ đọc, ADMIN không cấp
   ngầm; các business API khác vẫn deny. Kiểm tra 400/404/duplicate 409, ID string/UTC,
   filter/pagination, update giữ active/createdAt. Chỉ dùng record/namespace test đã duyệt.
5. Category đang có Product: deactivate 409, active/timestamps và Product/stock giữ nguyên.
   Không tự tạo/sửa Product để dựng fixture trên DB dùng chung; cần fixture được duyệt.
6. Trên fixture/connections được cho phép: concurrent duplicate code, update/deactivate,
   Product insert/reassignment vs deactivate; kiểm chứng UNIQUE extraction, physical
   locks, lock timeout/rollback và collation. Isolated PASS không thay các bước này.
7. Logout/CSRF rotation, session hết hạn; keyboard/native dialog focus trap, light/dark,
   responsive/touch/zoom/screen reader. Ghi evidence và đối soát dữ liệu; cleanup phải
   được duyệt riêng, không hard delete lịch sử hoặc restore/reset DB tự động.

CAT-001 giữ cả bốn cột IN_PROGRESS đến E2E/review/merge. Không MySQL/Flyway/bootstrap,
DB writes, accounts, runtime grants, commit/push trong nhiệm vụ chuẩn bị này.
