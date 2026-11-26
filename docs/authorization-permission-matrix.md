# Permission Matrix V1 — Authorization Part 3B-1

**Quyết định riêng Category V1 — theo yêu cầu Category Integration tại ce5019d:** chọn MANAGER → CATEGORY_READ/CREATE/UPDATE/DEACTIVATE; SALES_STAFF và WAREHOUSE_STAFF → CATEGORY_READ; không thêm quyền Category cho ADMIN. Đây là policy được chọn cho đúng slice Category, thay trạng thái đề xuất của bốn dòng Category trong snapshot bên dưới; không approve các phần khác của matrix, không đổi 60 codes/9 PROVISIONAL_DENY, không phải phê duyệt thực thi DB. Source chuẩn bị exact manifest: CategoryPermissionRollout; chưa có runtime grants.

Ngày 2026-11-21; branch `feature/authorization`, source đối chiếu tại `2e152a3` (Authorization Foundation Part 3A). Bản hoàn thiện Part 3B-1 phân biệt **BASELINE**, **USER-REQUIRED** (yêu cầu nhiều ADMIN trong nhiệm vụ hiện tại) và **PROVISIONAL/PENDING — DENY**. Giữ 60 tên permission đề xuất; không tự chuyển catalog thành APPROVED. Phần có căn cứ có thể làm nền tảng Part 3B-2 khi được giao triển khai, không chờ TV2 hoàn thành module; phần thiếu contract tiếp tục bị từ chối. Part 3B-2A đã thêm catalog/policy guard và isolated tests local (mục 10); chưa seed permission/role_permission, tích hợp enforcement vào business API hoặc mở API nghiệp vụ.

## 1. Nguồn và hiện trạng đã xác minh

- [project-scope.md](project-scope.md), mục 3–7: một cửa hàng, một kho, bốn role nhân viên; bán tại quầy, không có customer account, e-commerce, gateway, warranty, serial/IMEI hoặc multi-warehouse.
- [requirements.md](requirements.md), FR-AUTH/USR/CAT/PRD/SUP/CUS/PUR/INV/ORD/PAY/RET/RPT và mục 5: baseline chức năng và ma trận quyền mức cao. Quyền API chi tiết chưa được chốt; nhãn PROPOSED ở đây không sửa baseline đó.
- [database-design.md](database-design.md), mục 1.2, 3–7: định danh, mapping, trạng thái, audit, transaction, bất biến kho/tiền và quyền xác nhận nhập/duyệt adjustment. Các ghi chú stack cũ trong tài liệu không được dùng làm bằng chứng runtime hiện tại.
- [backend-conventions.md](backend-conventions.md), mục B/F/G, P03/P07/P08/P09: actor server-side, mặc định từ chối, permission matrix/endpoint mapping và một số chi tiết transaction/timezone còn PENDING.
- [implementation-tracker.md](implementation-tracker.md): 33 task IDs, 8 dependencies; giữ ownership của TV1 — **Trần Minh Nhựt**, TV2 — **Nguyễn Phạm Gia Huy**. Bản thiết kế này không tạo task ID mới hoặc chuyển module giữa hai thành viên.
- [authorization-foundation.md](authorization-foundation.md) và `AuthorizationService`, `RoleResolutionService`, `EmployeeRole`, `ResolvedAccess`: Part 3A đã commit/push `2e152a3`, có 126 isolated tests PASS ở lần kiểm tra trước. Actor lấy từ EmployeePrincipal, account được kiểm tra trên server; role/permission đọc qua mapping DB, snapshot immutable; ADMIN không có wildcard. Các code FIXTURE_* trong tests không phải permission catalog production.
- Flyway V1 `backend/src/main/resources/db/migration/V1__create_initial_schema.sql`: `app_role`, `permission`, `user_role`, `role_permission` đã có; chỉ seed bốn role. `Permission.code` là VARCHAR(100), UNIQUE, binary collation; liên kết UserRole/RolePermission là nhiều-nhiều qua FK. Không thấy catalog code nghiệp vụ cố định trong source production. **Không kết luận dữ liệu permission của MySQL hiện tại rỗng** vì nhiệm vụ này không kết nối DB; phải kiểm tra xung đột code/mapping hiện hữu trong nhiệm vụ vận hành riêng trước rollout.
- Source hiện có health/auth API, helper authorization và manual bootstrap. Các module nghiệp vụ mới có persistence; chưa có service/API CRUD nghiệp vụ để gán permission. `SecurityConfig` vẫn chỉ public health/csrf/login đúng method, me/logout yêu cầu authenticated, login/logout giữ CSRF; các API nghiệp vụ còn `denyAll`. Không đề xuất URL hay tạo endpoint mới ở đây.

## 2. Role, ký hiệu và nguyên tắc

| Role | Định hướng mapping đề xuất | Giới hạn theo baseline |
|---|---|---|
| ADMIN | Quản trị tài khoản nhân viên, tra cứu cấu hình role/permission phục vụ gán role. | Không tự có mọi quyền; đề xuất riêng INVENTORY_READ theo ô R của requirements mục 5. Các quyền nghiệp vụ khác chỉ thêm sau quyết định D08. |
| MANAGER | Quản lý danh mục/sản phẩm/đối tác thuộc phạm vi, sửa giá, giám sát, duyệt adjustment, xem báo cáo. | Không mặc định quản lý nhân viên hoặc xác nhận nhập hàng; các ô R không biến thành quyền ghi. |
| SALES_STAFF | Khách hàng, đơn bán, ghi nhận thanh toán/trả hàng; tra cứu sản phẩm/tồn. | Không đổi giá sản phẩm, tự đặt đơn giá bán hoặc CRUD số dư kho. Quyền refund/void chi tiết cần D06. |
| WAREHOUSE_STAFF | Phiếu nhập, xác nhận nhập, kiểm kê, đề xuất adjustment; tra cứu dữ liệu phục vụ kho. | Không tự bán hàng hoặc duyệt adjustment. Manager duyệt adjustment và người đề xuất không được tự duyệt. |

Trong các bảng dưới: **P** = mapping đề xuất có căn cứ chức năng/role từ baseline, dùng để chuẩn bị guard và tests; **H** = phương án giữ lại để thảo luận, **PROVISIONAL/PENDING, hiệu lực mặc định DENY, không gán quyền**; **—** = không đề xuất gán qua role đó. P không đồng nghĩa APPROVED, đã seed, đã triển khai hoặc được bỏ qua điều kiện G1–G8/D10/D12. D01–D12 là 12 quyết định ban đầu; D13 bổ sung contract nhiều ADMIN theo yêu cầu hiện tại, không phải tracker task ID. Review contract không thêm bước duyệt nghiệp vụ cho người dùng.

- Least privilege, deny by default: không có mapping được duyệt/triển khai thì không có quyền; permission tồn tại trong DB nhưng chưa gắn đúng role/user cũng không đủ. Không tự gán toàn bộ catalog cho ADMIN.
- Part 3A union quyền từ nhiều role thực tế; dấu — không phải deny override nếu một role khác đã được gán quyền đó. Không có role inheritance ngầm. H là giới hạn của contract phát hành tiếp theo, không phải tính năng mới đã có ở guard Part 3A: dù DB có sẵn mapping, chưa được chọn một use case H để mở khi chưa có quyết định. Business predicates, đặc biệt cấm tự duyệt adjustment, vẫn áp dụng cho user có nhiều role.
- Authentication xác nhận actor/active; authorization kiểm tra hành động và phạm vi; business validation kiểm tra dữ liệu/trạng thái/aggregate. Có permission không bỏ qua trạng thái chứng từ; `readOnly=true` không phải phân quyền. Thiếu danh tính trả 401, thiếu quyền 403; lỗi validation/conflict theo Shared ProblemDetail hiện có, không che lỗi nghiệp vụ bằng cách cấp quyền rộng hơn.
- Actor/audit ID lấy từ CurrentActorService hoặc guard, không từ body/query/header. Role IDs trong yêu cầu quản trị nhân viên là dữ liệu đích phải được server lookup/validate; không được dùng làm quyền của chính actor. Frontend chỉ hỗ trợ UX, không quyết định bảo mật.
- Đề xuất guard ở Service use case, endpoint policy tương ứng khi được duyệt; không duplicate policy giữa Controller và Service hoặc mở `/api/**`. Method HTTP, menu hiển thị, Entity/Repository methods không tự tạo quyền. Không biến quyền READ thành quyền ghi hay phép xem mọi trường.

## 3. Quy ước code và loại hành động — PROPOSED

Code dùng `RESOURCE_ACTION`, uppercase ASCII, chỉ `[A-Z0-9_]`, dài tối đa 100; là tập con hợp lệ của quy tắc database-design/Part 3A. Dùng danh từ thống nhất: EMPLOYEE, ROLE, PERMISSION, CATEGORY, PRODUCT, SUPPLIER, CUSTOMER, PURCHASE, INVENTORY, STOCKTAKE, ADJUSTMENT, SALE, PAYMENT, RETURN, REFUND, REPORT. Không đổi tên code đang dùng nếu chưa kiểm tra dữ liệu runtime và đánh giá tác động; không coi các ví dụ trong yêu cầu hoặc tests là code đã duyệt.

| Loại hành động | Ý nghĩa và ranh giới |
|---|---|
| READ | Danh sách/chi tiết/tìm kiếm trong scope và field projection đã duyệt; không cho mutation hoặc tự cho xem giá vốn/báo cáo. |
| CREATE / UPDATE | Tạo/sửa hồ sơ hoặc chứng từ ở trạng thái được phép; không bao gồm chuyển trạng thái, sửa giá, cấp role hoặc đặt actor/total tùy ý. |
| DEACTIVATE / LOCK / UNLOCK | Thay đổi trạng thái được requirement/design hỗ trợ, tách khỏi sửa thông tin thường; không đồng nghĩa thu hồi toàn bộ session đã triển khai. |
| DELETE | Chưa đưa quyền xóa hồ sơ/chứng từ vào catalog: điều kiện cấm xóa khi đã tham chiếu không tự xác lập use case được phép xóa. D11 cần xác nhận nhu cầu trước khi đề xuất CATEGORY_DELETE. Không dùng DELETE thay CANCEL, VOID, REFUND hoặc inactive; xóa dòng DRAFT thuộc UPDATE theo rule chứng từ. |
| CONFIRM / COMPLETE / CANCEL | Transition nghiệp vụ có điều kiện riêng; UPDATE không cho phép ghi trực tiếp cột status để bỏ qua guard transition. |
| ADJUST | Chỉ áp dụng delta qua ADJUSTMENT_APPROVE; PROPOSE chưa đổi tồn. Không có quyền set số dư inventory trực tiếp. |
| VOID / REFUND | VOID sửa payment nhập nhầm khi chưa thu tiền; REFUND tạo sự kiện hoàn tiền đã thu, liên kết payment gốc. Không thay thế cho nhau. |
| START / COUNT / PROPOSE / APPROVE / REJECT | Hành động kiểm kê/đề xuất/ra quyết định đã có căn cứ trong thiết kế; không thêm approval step cho purchase, sale hoặc return. |

## 4. Catalog kết hợp ma trận role × permission — 60 codes PROPOSED

Không đổi tên, thêm hoặc xóa code so với bản 60 permissions trước. Cột căn cứ và ký hiệu P/H phân biệt quyền có cơ sở với phương án bị giữ DENY. G1–G8 là điều kiện ở mục 5; D01–D12 ở mục 6, D13 ở mục 7. Một permission không ngầm bao gồm permission khác, kể cả READ. Có thể xây dựng contract/guard và isolated tests cho các ô P mà không chờ module TV2; mở API, phát hành seed hoặc chạy DB cần nhiệm vụ riêng và đầy đủ điều kiện use case.

### 4.1. Identity/Employee và Role/Permission — TV1

| Permission | Hành động | ADMIN | MANAGER | SALES_STAFF | WAREHOUSE_STAFF | Căn cứ / điều kiện |
|---|---|---|---|---|---|---|
| EMPLOYEE_READ | READ tài khoản nhân viên | P | — | — | — | FR-USR-01; G1, D01/D12; không hash/session/secret. |
| EMPLOYEE_CREATE | CREATE tài khoản | P | — | — | — | FR-USR-02; G1; gán role là quyền riêng. |
| EMPLOYEE_UPDATE | UPDATE thông tin được phép | P | — | — | — | FR-USR-03; không đổi active/role/secret qua cập nhật chung. |
| EMPLOYEE_LOCK | LOCK tài khoản | P | — | — | — | FR-USR-04–05; G1, D01; không khẳng định session cũ đã bị revoke. |
| EMPLOYEE_UNLOCK | UNLOCK tài khoản | P | — | — | — | FR-USR-04; G1, D01. |
| EMPLOYEE_ROLE_ASSIGN | Gán/bỏ role hiện có cho nhân viên | P | — | — | — | FR-USR-02, scope 3.1; G1, D08; validate target roles. |
| ROLE_READ | READ bốn role và mapping hiện có | H | — | — | — | Scope 3.1, FR-USR-02; dữ liệu hỗ trợ quản trị, phân rã PROPOSED. |
| PERMISSION_READ | READ catalog/mapping đã được duyệt | H | — | — | — | Scope 3.1, FR-AUTH-03; không cấp quyền sửa catalog/matrix. |

Không đề xuất CRUD role/permission, EMPLOYEE_DELETE, password-reset endpoint hoặc runtime editor cho role_permission vì chưa có use-case/API chi tiết được chốt. ROLE_READ/PERMISSION_READ giữ H: chưa mở API đọc toàn bộ mapping. User Management có thể validate/gán bốn role server-side bằng EMPLOYEE_ROLE_ASSIGN, không phụ thuộc hai quyền H; không suy việc chọn role thành quyền đọc mọi permission. Create kèm role assignment kiểm tra EMPLOYEE_CREATE và EMPLOYEE_ROLE_ASSIGN; quản trị nhân viên còn cần actor ADMIN (D01/D09/D13). ADMIN tiếp theo đi qua use case này, không gọi lại CLI.

### 4.2. Category và Product — TV1

| Permission | Hành động | ADMIN | MANAGER | SALES_STAFF | WAREHOUSE_STAFF | Căn cứ / điều kiện |
|---|---|---|---|---|---|---|
| CATEGORY_READ | READ danh mục | — | P | P | P | FR-CAT-01, requirements mục 5. |
| CATEGORY_CREATE | CREATE danh mục | — | P | — | — | FR-CAT-02; G2. |
| CATEGORY_UPDATE | UPDATE thông tin | — | P | — | — | FR-CAT-03; G2; không đổi active qua quyền chung. |
| CATEGORY_DEACTIVATE | Ngừng sử dụng | — | P | — | — | FR-CAT-04; G2. |
| PRODUCT_READ | READ sản phẩm/SKU/giá bán | — | P | P | P | FR-PRD-01/08, requirements mục 5; D12 về fields. |
| PRODUCT_CREATE | CREATE sản phẩm | — | P | — | — | FR-PRD-02; G2; giá khởi tạo cần D02/D09. |
| PRODUCT_UPDATE | UPDATE thông tin/MinStockLevel | — | P | — | — | FR-PRD-03/07; không bao gồm selling_price/is_active. |
| PRODUCT_PRICE_UPDATE | Thay đổi giá bán | — | P | — | — | FR-PRD-04; D02; không suy từ PRODUCT_UPDATE. |
| PRODUCT_DEACTIVATE | Ngừng kinh doanh | — | P | — | — | FR-PRD-05–06; G2; bảo toàn lịch sử. |

### 4.3. Supplier và Customer — TV1

| Permission | Hành động | ADMIN | MANAGER | SALES_STAFF | WAREHOUSE_STAFF | Căn cứ / điều kiện |
|---|---|---|---|---|---|---|
| SUPPLIER_READ | READ nhà cung cấp | — | P | — | P | FR-SUP-01, requirements mục 5; phục vụ lập phiếu nhập. |
| SUPPLIER_CREATE | CREATE nhà cung cấp | — | P | — | — | FR-SUP-02; G2. |
| SUPPLIER_UPDATE | UPDATE thông tin | — | P | — | — | FR-SUP-03; không bao gồm active. |
| SUPPLIER_DEACTIVATE | Ngừng sử dụng | — | P | — | — | FR-SUP-04–05; G2. |
| CUSTOMER_READ | READ hồ sơ khách | — | P | P | — | FR-CUS-01, requirements mục 5; D12 về dữ liệu cá nhân. |
| CUSTOMER_CREATE | CREATE hồ sơ giao dịch | — | — | P | — | FR-CUS-02/04; không tạo customer login. |
| CUSTOMER_UPDATE | UPDATE thông tin | — | — | P | — | FR-CUS-03; G2; không bao gồm active. |
| CUSTOMER_DEACTIVATE | Ngừng sử dụng hồ sơ | — | — | H | — | Database-design mục 1/3.2 và FR-CUS-05; không được FR-CUS mô tả trực tiếp, cần D11. |

Ưu tiên inactive và FK RESTRICT; không đề xuất CATEGORY_DELETE/PRODUCT_DELETE/SUPPLIER_DELETE/CUSTOMER_DELETE hoặc quyền khôi phục active mới khi chưa có contract. FR-CAT-05 cấm xóa khi được tham chiếu, chưa đủ căn cứ tạo quyền xóa trường hợp khác; D11 giữ câu hỏi này ngoài catalog. Không tự xóa inventory cùng product; lifecycle product/inventory còn DEP-001. Deactivate không xóa hoặc sửa các chứng từ lịch sử.

### 4.4. Purchasing — TV2

| Permission | Hành động | ADMIN | MANAGER | SALES_STAFF | WAREHOUSE_STAFF | Căn cứ / điều kiện |
|---|---|---|---|---|---|---|
| PURCHASE_READ | READ phiếu nhập/chi tiết | — | P | — | P | FR-PUR-10, requirements mục 5; D12. |
| PURCHASE_CREATE | CREATE DRAFT | — | — | — | P | FR-PUR-01–02; G3. |
| PURCHASE_UPDATE | UPDATE dòng/thông tin DRAFT | — | — | — | P | FR-PUR-03; G3; xóa dòng thuộc UPDATE, không xóa chứng từ đã confirm. |
| PURCHASE_CONFIRM | CONFIRM và Stock In | — | — | — | P | FR-PUR-04–09, DB-design 4.1; G3, D03. |
| PURCHASE_CANCEL | CANCEL DRAFT | — | — | — | P | DB-design 4.1, migration CHECK; G3; không hủy CONFIRMED. |

Manager chỉ READ; không thêm bước Manager duyệt phiếu nhập. Sai sau confirm phải qua adjustment theo baseline, không cấp PURCHASE_UPDATE/CANCEL để viết lại lịch sử.

### 4.5. Inventory, Ledger, Stocktake và Adjustment — TV2

| Permission | Hành động | ADMIN | MANAGER | SALES_STAFF | WAREHOUSE_STAFF | Căn cứ / điều kiện |
|---|---|---|---|---|---|---|
| INVENTORY_READ | READ tồn/khả dụng/cảnh báo thấp | P | P | P | P | FR-INV-01/11, requirements mục 5; D08/D12, không bao gồm giá vốn/report tổng hợp. |
| INVENTORY_LEDGER_READ | READ lịch sử movement/nguồn | — | P | — | P | FR-INV-02/03/12; scope role chi tiết PROPOSED, D12; không ghi/sửa/xóa ledger. |
| STOCKTAKE_READ | READ phiếu và kết quả kiểm kê | — | P | — | P | FR-INV-05, requirements mục 5; Manager R. |
| STOCKTAKE_START | Bắt đầu/chụp snapshot SKU | — | — | — | P | DB-design 4.5; G7; một kho, không tạo workflow approval mới. |
| STOCKTAKE_COUNT | Nhập/cập nhật số đếm | — | — | — | P | FR-INV-05; G7; không sửa system_quantity/quantity_on_hand từ client. |
| ADJUSTMENT_READ | READ đề xuất/kết quả | — | P | — | P | FR-INV-06–10, requirements mục 5. |
| ADJUSTMENT_PROPOSE | Tạo đề xuất có lý do | — | — | — | P | FR-INV-06–07; G7; không làm đổi tồn. |
| ADJUSTMENT_APPROVE | APPROVE và ADJUST tồn | — | P | — | — | FR-INV-08–10, DB-design 4.5/7; G7, D04; không tự duyệt. |
| ADJUSTMENT_REJECT | REJECT đề xuất | — | H | — | — | DB-design 4.5, trạng thái REJECTED; vai trò/audit chi tiết cần D11. |
| ADJUSTMENT_CANCEL | CANCEL đề xuất PENDING | — | — | — | H | DB-design mục 7, quy tắc 7; ai được hủy/ownership cần D10/D11. |

Không có INVENTORY_CREATE/UPDATE/DELETE hoặc INVENTORY_ADJUST kiểu set số dư tự do, không có LEDGER_CREATE/UPDATE/DELETE public. Ledger/balance chỉ được ghi nội bộ như side effect nguyên tử của confirm/cancel/return/adjustment đã hợp lệ; không buộc Sales Staff nhận quyền kho tổng quát để bán hàng. Chốt stocktake, chuyển PENDING_APPROVAL/COMPLETED, hủy stocktake khi adjustment bị reject là trạng thái/rule hệ thống hiện có; không tự thêm STOCKTAKE_APPROVE/CANCEL/SUBMIT như một workflow người dùng mới.

### 4.6. Sales, Payment, Return và Refund — TV2

| Permission | Hành động | ADMIN | MANAGER | SALES_STAFF | WAREHOUSE_STAFF | Căn cứ / điều kiện |
|---|---|---|---|---|---|---|
| SALE_READ | READ/tìm lịch sử đơn | — | P | P | — | FR-ORD-14, requirements mục 5; D10/D12. |
| SALE_CREATE | CREATE DRAFT | — | — | P | — | FR-ORD-01/03; G4. |
| SALE_UPDATE | UPDATE dòng/thông tin DRAFT | — | — | P | — | FR-ORD-02/03; không tự đặt đơn giá/total/status từ client. |
| SALE_CONFIRM | CONFIRM và Stock Out | — | — | P | — | FR-ORD-04–09/13; G4. |
| SALE_COMPLETE | Ghi bàn giao/COMPLETED | — | — | H | — | DB-design 4.2; vai trò phân rã PROPOSED; G4, D05. |
| SALE_CANCEL | CANCEL theo trạng thái | — | — | P | — | FR-ORD-10–12, DB-design mục 4.2 và mục 7, quy tắc 3; G4, D05/D06. |
| PAYMENT_READ | READ thanh toán và số còn thu | — | P | P | — | FR-PAY-03/05, requirements mục 5. |
| PAYMENT_CREATE | Ghi khoản thu RECORDED | — | — | P | — | FR-PAY-01–02/04/06; G5. |
| PAYMENT_VOID | VOID ghi nhầm, chưa thu tiền | — | — | H | — | DB-design 4.3; role chi tiết PROPOSED, G5, D06. |
| RETURN_READ | READ hồ sơ trả hàng | — | P | P | P | FR-RET-01–05, requirements mục 5; Warehouse R, D12. |
| RETURN_CREATE | CREATE DRAFT và dòng trả | — | — | P | — | FR-RET-01–03; G6. |
| RETURN_UPDATE | UPDATE DRAFT | — | — | P | — | DB-design 3.4/4.4; phân rã thao tác trên draft, G6. |
| RETURN_CONFIRM | CONFIRM, restock và refund | — | — | P | — | FR-RET-04–06, DB-design 4.4; G6, D06. |
| RETURN_CANCEL | CANCEL DRAFT | — | — | P | — | DB-design 4.4 và migration CHECK; G6, D10. |
| REFUND_READ | READ sự kiện hoàn tiền | — | H | H | — | DB-design 3.4/4.3, yêu cầu xem payment/return; D06/D12. |
| REFUND_CREATE | REFUND — ghi sự kiện trả tiền đã thu | — | — | H | — | DB-design mục 4.3/4.4 và mục 7, quy tắc 3–5; G5/G6; không phải CRUD refund độc lập, D06/D09. |

Các thao tác xóa dòng DRAFT nằm trong SALE_UPDATE/RETURN_UPDATE; không đề xuất xóa payment/refund/return/đơn đã tác động kho. Không thêm bước Manager duyệt đơn bán, return hoặc refund. Manager muốn thực hiện thay nhân viên cần quyết định mapping riêng; role giám sát không tự thành quyền ghi. REFUND_CREATE/PAYMENT_VOID/SALE_COMPLETE chưa đủ căn cứ actor chi tiết nên giữ H. Khi một transition bắt buộc refund mà quyền refund chưa được chốt, từ chối toàn bộ transition, không cho chạy phần kho trước.

### 4.7. Reports — TV1

| Permission | Hành động | ADMIN | MANAGER | SALES_STAFF | WAREHOUSE_STAFF | Căn cứ / điều kiện |
|---|---|---|---|---|---|---|
| REPORT_REVENUE_READ | READ doanh thu thuần | — | P | — | — | FR-RPT-01/05, DB-design mục 1/7; G8, D07. |
| REPORT_SALES_READ | READ số bán/trả | — | P | — | — | FR-RPT-02/05; G8, D07. |
| REPORT_INVENTORY_READ | READ báo cáo tồn | — | P | — | — | FR-RPT-03/05; G8; khác lookup khả dụng, D07. |
| REPORT_LOW_STOCK_READ | READ báo cáo tồn thấp | — | P | — | — | FR-RPT-04–05; G8; cảnh báo lookup có trong INVENTORY_READ. |

Không có quyền report lợi nhuận, export dữ liệu hàng loạt hoặc kế toán mới. REPORT_* dành cho Manager có căn cứ; nhân viên có lookup tồn không tự có reports. Chỉ projection tổng hợp đã định nghĩa mới được mở trong nhiệm vụ sau; drill-down, trường nhạy cảm hoặc ranh giới ngày chưa chốt tiếp tục DENY theo D07/D12/P07.

### 4.8. Phân loại hiệu lực đề xuất

Giữ nguyên **60 codes**: **51 codes** có ít nhất một mapping P dựa trên baseline; **9 codes** chỉ có H, chưa được cấp. Tổng các ô theo role không bằng số code vì một code có thể được đề xuất cho nhiều role.

| Role | Ô P có căn cứ | Ô H giữ DENY |
|---|---|---|
| ADMIN | 7 | 2 |
| MANAGER | 27 | 2 |
| SALES_STAFF | 18 | 5 |
| WAREHOUSE_STAFF | 16 | 1 |

P chỉ đủ căn cứ về loại hành động/role; thiếu state, ownership, projection hoặc transaction thì use case vẫn bị từ chối. H không là permission được cấp tạm. Không thêm code ngoài V1 hoặc code riêng CREATE_ADMIN; danh sách H chính xác ở mục 8.2.

## 5. Điều kiện trạng thái, transaction và ownership

Đây là điều kiện cần bên cạnh permission. Rule baseline vẫn bắt buộc; chỉ các phân rã/ownership còn thiếu căn cứ mới chờ quyết định. Các hành động ghi phải kiểm tra toàn bộ quyền/fields trước side effect, lấy actor từ server và có transaction phù hợp; không cho ghi từng phần rồi mới phát hiện thiếu quyền.

| Gate | Điều kiện và ranh giới |
|---|---|
| G1 — Identity | Account quản trị/actor ADMIN phải hợp lệ, có permission cho hành động; username/email chuẩn hóa/UNIQUE, BCrypt, không trả hash/secret. Create và role assignment atomic; chỉ bốn role hiện có. Kiểm tra bất biến còn ít nhất một ADMIN active theo D13 khi khóa/bỏ role ADMIN; update thường không nhận active/roles. Không đồng nghĩa session cũ đã bị invalidate. Tự khóa/tự bỏ ADMIN giữ DENY bảo thủ ở D13; không thêm bước duyệt nhân viên. |
| G2 — Master data | CREATE/UPDATE kiểm tra FK/UNIQUE/active, không sửa audit/actor qua request. PRODUCT_UPDATE không bao gồm giá, các UPDATE chung không bao gồm active/role. Đề xuất yêu cầu PRODUCT_PRICE_UPDATE cho mọi đường đổi selling_price, kể cả giá lúc tạo (D02/D09). Nếu D11 chấp thuận use case xóa danh mục trong tương lai thì phải không còn product tham chiếu, kể cả inactive; kiểm tra race và FK RESTRICT, không cascade. Hiện chưa có permission DELETE đề xuất. Lifecycle product/inventory DEP-001 không được giải quyết bằng xóa số dư ngầm. |
| G3 — Purchase | UPDATE/CANCEL chỉ DRAFT; CONFIRM đúng một lần, supplier/product active, đủ dòng hợp lệ/quantity dương, khóa inventory theo product_id, cộng tồn + PURCHASE_IN + status/actor/time atomic. CONFIRMED không sửa/xóa/hủy trực tiếp; sai sau confirm qua adjustment có duyệt. Không tạo purchase approval ngoài Warehouse confirm. |
| G4 — Sale | UPDATE chỉ DRAFT; giá/total/snapshot tính server-side, không discount/price override cho Sales qua request. CONFIRM kiểm tra toàn bộ SKU active/đủ tồn, trừ kho + SALE_OUT + trạng thái atomic. COMPLETE chỉ khi bàn giao thực tế, độc lập với đã thu đủ tiền. CANCEL DRAFT không movement; CANCEL CONFIRMED chỉ trước bàn giao, refund toàn bộ tiền thực thu nếu có và đảo toàn bộ SALE_OUT bằng SALE_CANCEL_IN trong một transaction. COMPLETED không hủy; sau bàn giao dùng return. D10 còn phải chốt phạm vi chứng từ mà từng nhân viên được thao tác. |
| G5 — Payment/Refund | Payment chỉ cho order CONFIRMED/COMPLETED, không DRAFT/CANCELLED; CASH/BANK_TRANSFER/CARD chỉ ghi nội bộ, không gateway hoặc dữ liệu thẻ. VOID chỉ bản ghi nhầm khi chưa thu tiền; tiền đã thu phải refund, không xóa/void để né ledger tiền. Refund dương gắn payment gốc, tổng refund không vượt payment. Refund không có return_id chỉ thuộc hủy đơn; có return_id phải đúng order và return. Khóa order/payment, tính aggregate và ghi atomic, không refund tùy ý. |
| G6 — Return | Đơn COMPLETED, thanh toán đủ, trong 7 ngày lịch; không đổi thành 168 giờ, ranh giới ngày/timezone P07 còn PENDING. Tổng lượng trả lũy kế không vượt bán; hoàn theo unit_price snapshot gốc. Chỉ phần bán lại được mới restock, 0 <= restock_quantity <= quantity; >0 mới có RETURN_IN. CONFIRM đúng một lần; inventory/ledger/refund/status trong cùng transaction, tổng refund bằng giá trị dòng trả. Chỉ DRAFT được sửa/hủy; không sửa CONFIRMED. |
| G7 — Stocktake/Adjustment | START snapshot dưới cùng inventory row lock với luồng movement. SKU có item chưa đếm IN_PROGRESS hoặc adjustment từ stocktake đang PENDING bị chặn movement theo DB-design; không khóa toàn cửa hàng. COUNT chỉ cập nhật số đếm, không set snapshot/tồn. Chỉ adjustment APPROVED mới đổi tồn + ADJUSTMENT_IN/OUT atomic; approver khác proposed_by kể cả actor có nhiều role. Delta không làm tồn âm; hủy chỉ PENDING, APPROVED/REJECTED là terminal. Khi reject stocktake-linked adjustment, xử lý hủy stocktake/kiểm kê lại theo rule hiện có; audit reject/cancel cụ thể cần D11. |
| G8 — Reports | Read-only dữ liệu server, giới hạn fields/filter/timezone theo scope. Doanh thu từ đơn COMPLETED trừ return CONFIRMED; không trừ payment_refund lần nữa; không tính lợi nhuận chưa có costing. Báo cáo số bán/trả/tồn phải đối soát nguồn, không tự cấp đọc mọi payment/purchase/user hoặc gọi mutation. |

Permission cho một use case có thể bao gồm side effect nội bộ hợp lệ, không phải quyền CRUD cho mọi bảng bị chạm. Phương án composite ở D06/D09 là permission transition AND REFUND_CREATE trước mọi thay đổi. Hiện REFUND_CREATE là H: return confirm có giá trị hoàn tiền hoặc cancel đã thu tiền phải bị từ chối toàn bộ cho tới khi chốt actor/mapping; không chạy kho/status trước rồi bỏ refund. Trường hợp số tiền thực sự bằng 0 chỉ bỏ gate refund nếu business contract use case xác nhận không có refund, không do client khai báo. Không thêm bước Manager duyệt để bù mapping thiếu. Các invariants kho/tiền/thứ tự khóa/chống lặp vẫn bắt buộc; retry/isolation/transaction owner còn P08/P09, nhưng không ngăn chuẩn bị guard/tests nền tảng.

## 6. Decisions Required — 12 quyết định đã có phương án cụ thể

**BASELINE** chỉ đánh dấu nội dung đã có ở requirements/database-design, không phê duyệt tên code hoặc toàn bộ mapping chi tiết. **PROPOSED** là cách phân rã kỹ thuật; phần chưa có căn cứ là **PROVISIONAL/PENDING — DENY**. Không quyết định nào được tự đóng APPROVED. Mỗi quyết định dưới đây tách phần có thể chuẩn bị trong Part 3B-2 khỏi phần chưa được cấp quyền; không lấy tiến độ module TV2 làm điều kiện bắt đầu nền tảng.

### D01 — MANAGER có quản lý nhân viên không?

- **Căn cứ:** [requirements.md](requirements.md) FR-USR-01: “Admin có thể xem danh sách tài khoản nhân viên”; FR-USR-02: “Admin có thể tạo tài khoản nhân viên và gán vai trò phù hợp.” Mục 5 chỉ ADMIN có dấu ✓ ở quản lý nhân viên; MANAGER là —.
- **Phương án:** chỉ ADMIN có mapping EMPLOYEE_READ/CREATE/UPDATE/LOCK/UNLOCK/ROLE_ASSIGN; MANAGER không được cấp. Giữ danh sách bốn role và quyền thực tế từ server, không có role tự khai báo hoặc tự đăng ký.
- **Tác động:** Backend dùng actor active, permission tương ứng và kiểm tra ADMIN bổ sung cho user administration; không suy ADMIN thành quyền business. Frontend chỉ cung cấp thao tác quản trị khi contract tương ứng được tích hợp, xử lý 401/403, không dùng ẩn menu thay guard. Nghiệp vụ không thêm cấp Manager phê duyệt nhân viên.
- **Trạng thái:** phân vai ADMIN-only là BASELINE; tách permission/AND role gate là PROPOSED cho Part 3B-2. Cấp user-management cho Manager tiếp tục DENY. Nhiều ADMIN và bảo vệ tài khoản cuối theo D13.

### D02 — Ai sửa giá sản phẩm?

- **Căn cứ:** [requirements.md](requirements.md) FR-PRD-04: “Chỉ Manager hoặc vai trò có quyền tương đương mới được thay đổi giá bán sản phẩm.” Mục 5 chỉ Manager có ✓ cho thay đổi giá; [database-design.md](database-design.md) mục 1/4.2 quy định không discount và snapshot giá lúc confirm.
- **Phương án:** mapping mặc định PRODUCT_PRICE_UPDATE chỉ MANAGER. PRODUCT_UPDATE không sửa selling_price; mọi đường thay giá, kể cả giá đầu tiên lúc create, cần quyền giá. Không gán quyền tương đương cho role khác khi chưa có quyết định.
- **Tác động:** Backend chuẩn bị field guard; create có giá cần PRODUCT_CREATE AND PRODUCT_PRICE_UPDATE; update gửi giá cần quyền giá trước mutation. Frontend tách trường/thao tác giá khỏi cập nhật thường; Sales dùng giá server. Nghiệp vụ không thêm duyệt giá, giảm giá hoặc thay snapshot lịch sử.
- **Trạng thái:** người sửa giá và bảo toàn snapshot là BASELINE; gate lúc create/cập nhật field là PROPOSED nhất quán least privilege. Quyền giá của ADMIN/Sales/Warehouse và client price override giữ DENY.

### D03 — Ai xác nhận nhập hàng?

- **Căn cứ:** [requirements.md](requirements.md) FR-PUR-04: “Warehouse Staff có thể xác nhận phiếu nhập hợp lệ.” [database-design.md](database-design.md) mục 6 ghi “Warehouse Staff xác nhận; Manager chỉ xem theo ma trận quyền”; mục 4.1 quy định DRAFT → CONFIRMED.
- **Phương án:** PURCHASE_CONFIRM chỉ WAREHOUSE_STAFF; Manager chỉ PURCHASE_READ. Warehouse tạo/sửa/hủy DRAFT; không có Manager approval hoặc confirm thay ngầm.
- **Tác động:** Backend có thể chuẩn bị requirePermission(PURCHASE_CONFIRM) và negative tests ngay; use case thực tế sau này kiểm tra supplier/product active, state/locks và atomic purchase + inventory + ledger. Frontend Warehouse có action theo state, Manager chỉ đọc. Nghiệp vụ giữ một phiếu nhập thống nhất, không thêm bước duyệt.
- **Trạng thái:** role/state/atomicity là BASELINE; tên permission và guard placement là PROPOSED. Manager/ADMIN xác nhận khi chỉ có các role đó tiếp tục DENY; không chờ service Purchasing hoàn thành để thiết kế guard.

### D04 — Ai điều chỉnh tồn kho?

- **Căn cứ:** [requirements.md](requirements.md) FR-INV-06 cho Warehouse tạo đề xuất; FR-INV-08: “Chỉ Manager hoặc vai trò có quyền tương đương mới được duyệt đề xuất điều chỉnh.” [database-design.md](database-design.md) mục 4.5: “Không cho người đề xuất tự duyệt adjustment của chính mình.”
- **Phương án:** ADJUSTMENT_PROPOSE cho Warehouse; ADJUSTMENT_APPROVE cho Manager. Chỉ APPROVE mới áp delta, không có quyền CRUD/set tồn. Không tự duyệt dù actor có đồng thời hai role; role khác không được suy thành “tương đương”.
- **Tác động:** Backend tách permission gate khỏi predicate actor.userId != proposed_by và transaction cập nhật kho/ledger/status. Frontend hiển thị đề xuất/duyệt theo contract nhưng không quyết định người duyệt. Nghiệp vụ chỉ giữ bước Manager duyệt vốn có.
- **Trạng thái:** phân vai, cấm tự duyệt và không âm tồn là BASELINE; có thể chuẩn bị guard/predicate tests. Quyền REJECT/CANCEL và audit còn PROVISIONAL/PENDING — DENY theo D11; không chặn việc chuẩn bị phần APPROVE đã có căn cứ.

### D05 — Ai hủy và hoàn tất đơn bán?

- **Căn cứ:** [requirements.md](requirements.md) FR-ORD-10: “Sales Staff có thể hủy đơn khi trạng thái và business rule cho phép.” [database-design.md](database-design.md) mục 4.2 cho DRAFT cancel, CONFIRMED cancel trước bàn giao, không cancel COMPLETED; COMPLETE khi bàn giao thực tế, độc lập payment status.
- **Phương án:** SALE_CANCEL cho Sales theo state; không cấp mặc định cho Manager/ADMIN. SALE_COMPLETE vẫn giữ H: phương án sau review là Sales ghi nhận bàn giao, nhưng baseline chưa chỉ rõ actor riêng cho transition này. Không tự suy từ completed_by.
- **Tác động:** Backend có thể thiết kế guard cancel; CONFIRMED cancel cần reversal đầy đủ và refund tiền đã thu atomic, phần refund chưa rõ thì từ chối toàn bộ (D06). Frontend phân biệt hủy DRAFT, hủy trước bàn giao và trả hàng; không kích hoạt nút Complete chỉ vì có SALE_CONFIRM. Nghiệp vụ không hủy đơn COMPLETED hoặc thêm Manager duyệt hủy.
- **Trạng thái:** SALE_CANCEL cho Sales/state là BASELINE; actor SALE_COMPLETE là PROVISIONAL/PENDING — DENY. Ownership theo D10; cancel có tiền hoàn chưa đủ điều kiện mở.

### D06 — Ai thực hiện refund và payment void?

- **Căn cứ:** [requirements.md](requirements.md) FR-PAY-01 cho Sales ghi nhận khoản thu; FR-RET-06 chỉ cho ghi nhận hoàn tiền nội bộ theo business rule. [database-design.md](database-design.md) mục 4.3: “VOIDED chỉ dùng để sửa bản ghi nhập nhầm khi tiền chưa thực sự được thu”; return refund phải ghi cùng transaction. Các đoạn này chưa chỉ rõ role thực hiện void/refund độc lập.
- **Phương án:** giữ REFUND_CREATE, REFUND_READ, PAYMENT_VOID ở H, không gán cho Sales hoặc Manager ở bản phát hành chưa chốt actor. Phương án để review là Sales thao tác trong cancel/return, Manager chỉ đọc projection cần thiết; không endpoint refund tùy ý, không thêm approval step.
- **Tác động:** Backend chuẩn bị deny/atomicity tests: transition cần refund phải kiểm tra transition permission AND REFUND_CREATE trước mọi mutation; khi H còn hiệu lực thì toàn bộ transition bị từ chối. PAYMENT_CREATE vẫn có căn cứ riêng; không dùng VOID để trả tiền đã thu. Frontend báo hành động chưa khả dụng, không ghi nhận thành công một phần hoặc chuyển sang Manager approval tự tạo. Nghiệp vụ giữ đủ dấu vết tiền và liên kết payment gốc.
- **Trạng thái:** ý nghĩa void/refund, aggregate và atomicity là BASELINE; actor/mapping, REFUND_READ projection và composite permission chi tiết là PROVISIONAL/PENDING — DENY. Return confirm có giá trị hoàn tiền và cancel đã thu tiền chưa được mở; không lấy tiến độ module TV2 làm lý do trì hoãn negative tests.

### D07 — Nhân viên được xem báo cáo tới mức nào?

- **Căn cứ:** [requirements.md](requirements.md) FR-RPT-01: “Manager có thể xem báo cáo doanh thu cơ bản theo khoảng thời gian”; FR-RPT-02–05 cho số bán, tồn, tồn thấp từ dữ liệu hệ thống; mục 5 chỉ Manager có ✓ cho reports. [database-design.md](database-design.md) mục 1 loại lợi nhuận và trừ refund hai lần.
- **Phương án:** bốn REPORT_* chỉ MANAGER. Sales/Warehouse dùng lookup sản phẩm/tồn và chứng từ trong nhiệm vụ, không được reports tổng hợp mặc định; ADMIN cũng không tự được reports.
- **Tác động:** Backend có thể chuẩn bị mapping/guard bốn report và kiểm tra role còn lại bị từ chối. Query triển khai sau chỉ dùng projection tổng hợp; time-boundary theo P07 chưa rõ thì chưa mở filter đó. Frontend chưa cung cấp export/drill-down hoặc report lợi nhuận. Nghiệp vụ giữ công thức doanh thu COMPLETED trừ return CONFIRMED.
- **Trạng thái:** Manager-only và loại báo cáo là BASELINE; field/filter/drill-down chưa được mô tả phải PROVISIONAL/PENDING — DENY, không mở cả dữ liệu nguồn chỉ vì có REPORT_READ.

### D08 — ADMIN có quyền nghiệp vụ nào?

- **Căn cứ:** [requirements.md](requirements.md) mục 5 có R cho Admin ở “Xem tồn kho”, các ô business khác là —; [database-design.md](database-design.md) mục 3.1 tách app_role, permission, user_role, role_permission. Schema tách role với các permission được gán, không quy định ADMIN có mọi permission.
- **Phương án:** ADMIN có EMPLOYEE_* theo D01 và INVENTORY_READ theo baseline; ROLE_READ/PERMISSION_READ chưa mở theo D11/D12. Mỗi quyền business bổ sung phải có mapping rõ; không wildcard, role inheritance hoặc hardcode bỏ qua guard. Gán một trong bốn role, kể cả ADMIN tiếp theo, theo D13.
- **Tác động:** Backend tiếp tục dùng mapping server qua Part 3A, kiểm tra union nhiều role nhưng giữ predicate nghiệp vụ; không có ADMIN bypass. Frontend không coi tên ADMIN là quyền hiện mọi nút. Nghiệp vụ có thể có nhiều quản trị viên, từng tài khoản độc lập; role quản trị không thay nhân viên kho/bán hàng.
- **Trạng thái:** không wildcard và INVENTORY_READ là BASELINE; mở thêm quyền business cho ADMIN là PROVISIONAL/PENDING — DENY. Quyền quản trị ADMIN tiếp theo là USER-REQUIRED, không phải cấp business wildcard.

### D09 — Role-only hay permission cụ thể?

- **Căn cứ:** [requirements.md](requirements.md) FR-AUTH-05 yêu cầu từ chối khi không có quyền; NFR-SEC-04: “authorization phải được kiểm tra ở backend.” [database-design.md](database-design.md) mục 3.1 đã có mapping permission, và Part 3A có requirePermission/requireAnyRole; không phải bắt đầu từ contract trống.
- **Phương án:** mọi use case mới trong catalog dùng requirePermission(code do server chọn); không business use case mới nào chỉ dựa role. User Management dùng ADMIN AND permission tương ứng (D01/D13). requireAnyRole không được OR với permission để bypass. Public/auth routes giữ nguyên policy hiện tại, không thêm permission login/logout.
- **Tác động:** Backend có thể chuẩn bị constants/allowlist phần P, adapters/checks dùng AuthorizationService hiện có và isolated tests 401/403 ngay. Không gọi guard Part 3A với code H rồi coi một mapping DB tồn tại là phê duyệt contract. Frontend không gửi requiredPermission/role để quyết định quyền; /me hiện tại chưa được mở rộng. Nghiệp vụ có một contract hành động, không thêm approval.
- **Trạng thái:** kiểm tra phía server và 401/403 là BASELINE; chọn permission cho use case, AND gate và code names là PROPOSED cho triển khai nền tảng. Endpoint/method chưa có mapping, code H và mọi đường chưa được cho phép tiếp tục DENY.

### D10 — Trạng thái chứng từ và ownership?

- **Căn cứ:** [database-design.md](database-design.md) mục 4.1–4.5 và mục 7 chốt state/transaction; mục 3 có created_by/proposed_by/performed_by nhưng sự tồn tại cột audit không tự định nghĩa quyền. [requirements.md](requirements.md) FR-ORD-10/14, FR-PUR-10 chưa quy định staff được thao tác chứng từ của người khác.
- **Phương án:** áp dụng G1–G8 bắt buộc. Chọn mặc định bảo thủ cho thiết kế: staff chỉ sửa/hủy/confirm draft do mình lập; stocktake count theo performed_by; người khác thao tác ghi bị DENY nếu chưa có scope rõ. Đây là ownership PROVISIONAL, không biến thành baseline hoặc cấp quyền chỉ nhờ là creator. Manager duyệt adjustment của người khác bằng permission là ngoại lệ có căn cứ sẵn.
- **Tác động:** Backend chuẩn bị predicate nhận actor từ guard và owner từ DB, kiểm tra trong transaction trước side effect; permission AND scope AND state, không nhận owner từ client. Frontend không dùng việc sở hữu để tự mở quyền, không thêm chức năng bàn giao ca/reassign. Nghiệp vụ cross-owner và ownership payment/return theo order hay theo chứng từ phải chốt riêng; chưa rõ thì từ chối use case đó.
- **Trạng thái:** state/atomicity/cấm tự duyệt là BASELINE. Ownership own-only, scope đọc staff và cross-owner là PROVISIONAL/PENDING; có thể viết predicate và deny tests, chưa mở action thiếu scope kể cả ô permission là P. Không chờ toàn bộ module hoàn thành để mô hình hóa điều kiện.

### D11 — Các quyền phân rã thiếu contract xử lý thế nào?

- **Căn cứ:** [requirements.md](requirements.md) FR-CAT-05 cấm xóa khi tham chiếu; FR-CUS-05 cấm làm mất lịch sử, không chỉ rõ actor deactivate. [database-design.md](database-design.md) mục 1/3.2 có inactive; mục 4.5/7 có reject/cancel adjustment. Migration V1 không có rejected_by/at/reason, REJECTED buộc approved_by/at NULL; enum không xác lập ai được thao tác.
- **Phương án:** CATEGORY_DELETE tiếp tục ngoài catalog. CUSTOMER_DEACTIVATE, ADJUSTMENT_REJECT/CANCEL, ROLE_READ và PERMISSION_READ giữ H. Phương án cần review: Sales deactivate hồ sơ, Manager reject adjustment, proposer hủy PENDING, Admin tra cứu role/permission trong projection giới hạn; chưa đủ căn cứ thì không cấp.
- **Tác động:** Backend không phát hành seed/mapping các quyền H hoặc endpoint tương ứng; không ghi rejector vào approved_by, không tự thêm cột/audit store. Role assignment validate bốn role có sẵn, không cần CRUD catalog. Frontend không dựng nút xóa/reject hoặc màn hình sửa permission chưa được hỗ trợ. Nghiệp vụ không thêm stocktake approval hay stage duyệt mới.
- **Trạng thái:** bảo toàn lịch sử/các state hiện có là BASELINE; actor, dữ liệu đọc role/permission và audit reject là PROVISIONAL/PENDING — DENY. Vẫn giữ tên code H để truy vết bản đề xuất, không phải catalog được phép seed.

### D12 — Quyền READ được xem những trường nào?

- **Căn cứ:** [requirements.md](requirements.md) FR-PRD-01 cho xem thông tin sản phẩm, FR-INV-01 cho xem tồn, FR-ORD-14 cho tra cứu; NFR-MNT-02: “API sử dụng DTO/request-response model phù hợp thay vì mặc định expose trực tiếp toàn bộ entity database.” [database-design.md](database-design.md) mục 3 tách giá nhập purchase, hash, thông tin khách và payment/refund.
- **Phương án:** READ chỉ trả projection cần cho use case. Lookup Product: ID/SKU/tên/category/giá bán/active và thông tin sản phẩm phù hợp; Inventory: sản phẩm/số lượng/ngưỡng thấp. Không bao gồm unit_cost, hash, session/secret, toàn bộ UserRole/RolePermission hoặc payment/refund khi chỉ có lookup. Báo cáo Manager chỉ projection tổng hợp; Warehouse RETURN_READ không mở thông tin thanh toán.
- **Tác động:** Backend có thể làm tests chống overexposure cho projection tối thiểu; fields nhạy cảm hoặc scope record chưa được contract xác định thì bỏ khỏi DTO hoặc từ chối use case nếu không thể tách an toàn. Không tự đoán route/DTO mới. Frontend dùng response được cho phép, không tải Entity rồi ẩn trường. Nghiệp vụ không phát sinh export hoặc quyền đọc mọi bảng liên quan.
- **Trạng thái:** DTO tối thiểu và backend bảo vệ quyền là BASELINE; các field list và scope lịch sử/cost/drill-down ngoài phạm vi đã duyệt vẫn PROVISIONAL/PENDING — DENY ngoài projection đã xác định. H của REFUND_READ/ROLE_READ/PERMISSION_READ chưa được mở bằng một quyền READ khác.
- **APPROVED — Customer Contact V1:** Chủ dự án duyệt riêng Customer Management: `CUSTOMER_READ` được đọc phone, email, address và notes trong chi tiết khách hàng; `CUSTOMER_CREATE`/`CUSTOMER_UPDATE` ghi các trường này theo [Customer API](customer-management-api.md#d12-customer-contact-v1). Giữ mapping role hiện có. Các quyết định D12 cho Sales, Payments, Reports và các projection khác vẫn theo phạm vi hiện hành.

## 7. D13 — Multiple ADMIN accounts và ADMIN active cuối cùng

**Trạng thái: USER-REQUIRED / CONTRACT ONLY.** Yêu cầu hiện tại chốt hỗ trợ nhiều ADMIN, tài khoản riêng và bảo vệ ADMIN active cuối; cơ chế khóa/validation bên dưới là PROPOSED để triển khai và kiểm thử ở nhiệm vụ sau. Không phải đã có code bảo vệ trong Part 3A hoặc Bootstrap.

### 7.1. Căn cứ và vòng đời tài khoản

- [requirements.md](requirements.md) FR-USR-02 cho Admin tạo nhân viên và gán role, FR-USR-04 cho khóa/mở, FR-USR-06 bảo toàn lịch sử. [database-design.md](database-design.md) mục 3.1 và migration V1 chỉ UNIQUE(user_id, role_id), không UNIQUE(role_id), nên nhiều user có role ADMIN đã được schema hỗ trợ; không cần sửa V1/Entity.
- Mỗi nhân viên có AppUser/username riêng, không chia sẻ tài khoản hoặc mật khẩu quản trị. Không có public self-registration, default user/password hay quyền ADMIN do frontend khai báo.
- CLI local giữ nguyên: manual --bootstrap-admin, chỉ computer_store_dev, chỉ tạo ADMIN đầu tiên. AdminBootstrapService từ chối nếu đã có bất kỳ ADMIN assignment nào, kể cả tài khoản inactive; không dùng lại CLI để thêm ADMIN, phục hồi account hoặc bỏ qua phân quyền.
- ADMIN tiếp theo do ADMIN active có EMPLOYEE_CREATE AND EMPLOYEE_ROLE_ASSIGN tạo qua User Management trong một transaction user + link. Gán ADMIN cho nhân viên đã tồn tại cần EMPLOYEE_ROLE_ASSIGN và actor ADMIN; role đích phải được lookup từ bốn role server, không tin request như quyền của người gọi. Không thêm code CREATE_ADMIN, role mới hoặc bước phê duyệt người thứ hai.
- Phần User Management/API/mapping thực tế chưa triển khai. Khi chưa có permission mapping cho người quản trị đầu tiên, không gọi lại CLI hoặc thêm ADMIN shortcut để làm cho chức năng chạy; việc phát hành mapping quản trị tối thiểu thuộc nhiệm vụ triển khai riêng.

### 7.2. Bất biến bảo vệ ADMIN cuối cùng

Sau khi đã khởi tạo ít nhất một ADMIN active, mọi mutation tài khoản/role được hỗ trợ phải giữ:

```text
activeAdminCount = COUNT(DISTINCT app_user.id)
                  với app_user.is_active = true
                  và tồn tại user_role -> app_role.code = 'ADMIN'

activeAdminCount sau transaction >= 1
```

- Count theo user active thực tế, không theo số session, tổng tài khoản ADMIN kể cả inactive, quyền client hoặc số role của một người. Tài khoản có nhiều role chỉ được tính một lần.
- Khóa/deactivate một ADMIN hoặc gỡ role ADMIN phải kiểm tra trạng thái sau thay đổi. Nếu làm số ADMIN active xuống 0 thì từ chối toàn transaction, kể cả actor đang đổi tài khoản của người khác. Không có đường EMPLOYEE_UPDATE chung để sửa active/roles và bypass guard.
- Không xóa vật lý account có lịch sử; không thay “gỡ role” bằng xóa user. Không đổi active hoặc bỏ role sau khi đã trả thành công một phần.
- **Mặc định bảo thủ PROVISIONAL/PENDING:** chưa hỗ trợ tự khóa hoặc tự gỡ ADMIN qua các use case này; từ chối dù còn ADMIN khác. Có thể xem xét nới sau khi có contract session/revocation, không coi đây là quy tắc đã có trong FR-USR.
- Nếu dữ liệu ban đầu đã có 0 ADMIN active nhưng có ADMIN assignment inactive, dừng thao tác quản trị cần actor ADMIN, không tự bootstrap phục hồi. Khôi phục vận hành cần task riêng, không viết SQL recovery/credentials ở đây.

### 7.3. Transaction, đồng thời và tác động

- Đây là business invariant/transaction validation sau authorization, không phải thêm permission và không phải workflow phê duyệt. Thiếu authentication: 401; thiếu role/permission: 403; có quyền nhưng thao tác làm mất ADMIN active cuối: business conflict, đề xuất 409 theo conventions, chưa thêm ErrorCode mới.
- Phương án khóa dùng ADMIN role row hiện có làm điểm serialize, nhất quán với Bootstrap đang khóa row này. Mọi đường quản trị tạo/gán/bỏ role hoặc đổi active phải đi qua cùng contract, khóa role trước các user/link liên quan theo thứ tự xác định; kiểm tra lại actor, mapping và tập ADMIN active từ dữ liệu hiện hành dưới khóa trước mutation/commit. Không chỉ SELECT COUNT rồi update; snapshot cũ trong transaction/persistence context không chứng minh invariant.
- Chi tiết current/locking read và lock order đầy đủ là PROPOSED, cần triển khai/tests đồng thời chứng minh; không tự chọn isolation hoặc khẳng định đã khóa đúng trên MySQL. Nếu không bảo đảm serialized validation thì giữ DENY các thao tác có thể làm mất ADMIN cuối.
- Tests cần có: tạo ADMIN thứ hai với đúng hai permission; anonymous/Manager/thiếu quyền không tạo được; không còn Admin active sau lock/bỏ role thì rollback; Admin inactive không tính; nhiều role không nhân count; hai ADMIN đồng thời khóa/gỡ nhau không thể cùng thành công làm count=0; tự khóa/gỡ bị từ chối theo mặc định; lỗi tạo link rollback user. Bootstrap giữ nguyên các test existing-admin, không mở nhánh recovery.
- Backend: triển khai ở Identity/User Management sau này, không sửa schema hoặc Bootstrap để cho thêm ADMIN. Frontend: mỗi người đăng nhập tài khoản riêng; hiển thị lỗi bảo vệ ADMIN cuối từ server, không lấy count phía client làm quyết định. Nghiệp vụ: thêm quản trị viên qua kênh đã phân quyền, không dùng chung account, không thêm cấp duyệt tài khoản.
- Session invalidation/revocation và tránh mất quyền quản trị do sửa role_permission vẫn là phần cần contract riêng; bảo vệ số lượng ADMIN active không chứng minh account có đủ permission runtime. Trước rollout phải kiểm tra mapping quản trị tường minh; tài liệu không tạo mapping đó.

## 8. Readiness cho Part 3B-2 và phần tiếp tục DENY

### 8.1. Có thể bắt đầu ngay khi có nhiệm vụ triển khai

Không cần chờ TV2 hoàn thành module hoặc đóng toàn bộ D01–D12. Có thể làm từng phần độc lập trên contract hiện có:

1. Chuẩn hóa constants/metadata phần P (51 codes có ít nhất một ô P), phân biệt chín code H; không tự seed, cấp quyền hoặc coi constants là mapping runtime.
2. Chuẩn bị policy/guard adapters dùng AuthorizationService Part 3A, code do server chọn; deny unknown/unmapped/H tại policy phát hành, anonymous 401, thiếu quyền 403, multi-role union, ADMIN không wildcard. Không thay primitive Part 3A thành mapping role hardcode.
3. Chuẩn bị tests cho mapping có căn cứ: Admin quản trị nhân viên/đọc tồn; Manager giá/adjustment approval/reports; Warehouse purchase/stocktake/proposal; Sales customer/sale/payment; kiểm tra quyền READ đúng mức. Fixtures được ghi là tests, không làm seed production.
4. Mô hình hóa/tests field guard cho giá, cấm tự duyệt adjustment và last-active-ADMIN invariant D13; ownership/field predicates provisional chỉ được chuẩn bị với nhãn rõ, không enable use case chưa chốt scope. User Management tạo ADMIN tiếp theo được thiết kế ngay từ contract hiện tại; implementation là nhiệm vụ riêng.
5. Ghi guard-to-action contract cho phần có căn cứ; giữ SecurityConfig denyAll các API chưa được tích hợp. Khi service/endpoint từng module có thật mới gắn method/route chính xác và tests tích hợp. Không chặn việc làm foundation vì chưa có code module, cũng không tạo endpoint giả để có chỗ đặt guard.

Đủ thiết kế để bắt đầu foundation không đồng nghĩa API sẵn sàng public, đã nghiệm thu MySQL, seed được phê duyệt hoặc tất cả 60 code trở thành APPROVED.

### 8.2. Phải giữ deny-by-default

| Phần chưa đủ căn cứ/điều kiện | Mặc định hiệu lực | Điều kiện mở trong nhiệm vụ sau |
|---|---|---|
| ROLE_READ, PERMISSION_READ, CUSTOMER_DEACTIVATE, ADJUSTMENT_REJECT, ADJUSTMENT_CANCEL, SALE_COMPLETE, PAYMENT_VOID, REFUND_READ, REFUND_CREATE | H — PROVISIONAL/PENDING, không cấp mapping | Chốt actor/action/projection và audit phù hợp; ghi bằng chứng quyết định riêng cho code/use case. |
| Cancel có tiền hoàn / Return confirm có giá trị hoàn | DENY toàn transition khi REFUND_CREATE chưa được chốt | Mapping refund cụ thể, composite guard và transaction refund + ledger + state rollback đầy đủ. |
| Ghi chứng từ của người khác; action/READ chưa có record scope hoặc field projection | DENY phần scope chưa xác định; không suy creator có quyền | Chốt ownership D10 và projection D12 cho use case cụ thể. |
| Manager user administration/purchase confirm; ADMIN business khác INVENTORY_READ; staff reports | Không gán mặc định qua các role đó | Quyết định mapping bổ sung có căn cứ; không lấy UI hoặc quyền role khác làm shortcut. |
| Tự khóa/tự gỡ ADMIN; thao tác làm activeAdminCount = 0 hoặc không bảo đảm kiểm tra đồng thời | DENY; trường hợp invariant là business conflict | Hoàn thiện invariant/transaction tests; chỉ nới tự thao tác nếu có quyết định/session contract riêng. |
| Endpoint business chưa được tích hợp, route/method hoặc permission chưa có policy | Giữ denyAll | Service guard + state/ownership/transaction + endpoint policy và tests tương ứng; module nào sẵn sàng thì tích hợp module đó. |
| Date boundary P07, lock/transaction/retry P08/P09 chưa rõ ở use case phụ thuộc | Không thực thi phần use case còn thiếu điều kiện | Chốt chi tiết cần thiết, tests phù hợp; không trì hoãn guard/negative tests độc lập. |

### 8.3. Phối hợp TV1/TV2 và bằng chứng còn thiếu

TV1 — Trần Minh Nhựt chủ trì Identity/catalog/partners/reports và foundation; TV2 — Nguyễn Phạm Gia Huy review mapping Purchasing/Inventory/Sales/Payment/Return theo contract hiện có, không phải chờ triển khai xong mới review. Giữ ownership và dependencies. Các quyết định còn H hoặc scope chưa rõ được xử lý riêng; không yêu cầu “mọi decision APPROVED” mới bắt đầu toàn bộ Part 3B-2.

Cần lưu bằng chứng review khi chốt/nới một phần provisional; đối chiếu permission/mapping DB hiện hữu trước rollout trong task cho phép DB, không suy database rỗng. MySQL query/concurrency, race đổi quyền/account, session revocation và browser/accessibility QA vẫn chưa được chứng minh. Không dùng 126 mock/isolated tests Part 3A làm bằng chứng enforcement hoặc last-ADMIN guard đã tồn tại.

## 9. Phạm vi Part 3B-1 — snapshot trước Part 3B-2A

Chỉ hoàn thiện tài liệu này và ghi chú tiến độ IAM-002/006 trong tracker, kèm lịch sử cập nhật; giữ 33 task IDs, 8 dependencies, tên thành viên, ownership và mọi trạng thái, không thêm DONE hoặc sửa task TV2. Giữ nguyên 60 tên permission; thay cách phân loại mapping P/H để không gán quyền khi thiếu căn cứ. D13 là contract nhiều ADMIN được yêu cầu trong lượt này; không phải tạo account hoặc sửa Bootstrap.

Không thay Backend/Frontend, Entity/Repository, SecurityConfig, migration/schema hoặc dependency. Không tạo seed, mở API, kết nối MySQL, chạy Flyway/bootstrap, tạo ADMIN, commit/push hoặc merge. Không chạy lại tests cho Markdown; 126 PASS là kết quả Part 3A trước đó. Dừng sau hoàn thiện Part 3B-1, chưa triển khai Part 3B-2.

## 10. Trạng thái kỹ thuật Part 3B-2A — local, chưa commit/push

Phần 1–8 là contract thiết kế; mục 9 giữ snapshot phạm vi Part 3B-1. Implementation mới chỉ hiện thực hóa catalog và policy boundary, không approve D01–D13.

- PermissionCode trong identity.authorization giữ đúng 60 tên ở mục 4, grouping 16 module: 51 ELIGIBLE_PROPOSED và 9 PROVISIONAL_DENY. Không chứa mapping role hoặc default grants; P/H trong ma trận vẫn là trạng thái đề xuất, không trở thành quyền runtime.
- PermissionPolicy.requirePermission gọi AuthorizationService Part 3A để kiểm tra actor/server mapping trước, rồi kiểm tra catalog/status. Unknown hoặc PROVISIONAL_DENY bị từ chối kể cả DB có mapping; anonymous/inactive giữ 401, authenticated bị từ chối giữ 403/detail chung. Không normalize code hoặc lấy code quyết định từ client.
- Tích hợp tương lai của TV1/TV2 phải dùng policy này cho permission contract; raw guard Part 3A giữ nguyên và không thay thế bước catalog eligibility. Vẫn cần state, scope/ownership, field-level checks và transaction của từng use case; policy không tự enforce những business predicates đó.
- Tests mới 94 PASS (5 catalog, 70 policy, 19 mapping/MVC), cộng 126 regression cũ = 220/220 PASS; không MySQL/datasource/Flyway/bootstrap thật. Chi tiết và lệnh chạy tests mới tại [authorization-foundation.md](authorization-foundation.md).
- SecurityConfig và auth/session/CSRF không đổi; API nghiệp vụ vẫn denyAll. Không có seed permission/role_permission, role mapping runtime mới, ADMIN wildcard, account mới hoặc last-active-ADMIN guard; D13 vẫn chỉ là contract. Catalog/mapping/API approval, 9 H và các scope/transaction/session còn thiếu vẫn PENDING.
