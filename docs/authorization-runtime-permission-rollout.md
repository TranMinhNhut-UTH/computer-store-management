# Runtime Permission Seed và Role Grant Rollout — Part 3B-2B

Ngày 2026-11-21; branch `feature/authorization`, HEAD `e251d0c`. Thiết kế Part 3B-2B.1 được cập nhật ở Part 3B-2B.2 với helper manifest validation/fingerprint và isolated tests. **Catalog registration/migration vẫn PROPOSED/PENDING, chưa có manifest APPROVED, chưa seed hoặc thực thi Stage A; Stage B chưa triển khai.** D01–D13 không tự được phê duyệt. Kết quả hiện tại: 29 tests mới + 220 regression = 249/249 PASS, không MySQL/Flyway; không chứng minh rollout thực tế.

## 1. Nguồn và hiện trạng đã kiểm tra

- [project-scope.md](project-scope.md), [requirements.md](requirements.md): bốn role nhân viên, một cửa hàng/kho, ADMIN không tự có toàn bộ quyền nghiệp vụ; FR-AUTH-03–05, FR-USR-01–06, NFR-SEC/DAT/AUD/MNT.
- [database-design.md](database-design.md), mục 1.2/3.1/8: identifier canonical, FK lịch sử RESTRICT, mapping nhiều-nhiều và migration versioned.
- [authorization-foundation.md](authorization-foundation.md), [authorization-permission-matrix.md](authorization-permission-matrix.md), [implementation-tracker.md](implementation-tracker.md): contract hiện có, P/H và các quyết định chưa duyệt. Các đoạn ghi “local/chưa commit” trong tài liệu Part 3B-2A là snapshot trước checkpoint; code/catalog/policy đã commit/push tại `e251d0c`.
- Source đối chiếu: PermissionCode, PermissionPolicy, AuthorizationService, RoleResolutionService; AppRole, Permission, RolePermission, UserRole và bốn Repository liên quan; migration `V1__create_initial_schema.sql`, AdminBootstrapService và `application.properties`.
- POM có Flyway starter/core qua dependency management và flyway-mysql, không khai báo Flyway Maven plugin hoặc CLI/runner vận hành riêng. Classpath báo cáo test gần nhất ghi flyway-core/mysql **12.4.0**; phải xác minh lại artifact deploy, không suy phiên bản CLI từ bản web documentation.
- Properties dùng `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`; Hibernate `ddl-auto=validate`; Flyway `enabled=true`, `locations=classpath:db/migration`. Không đọc biến credentials hoặc kết nối DB trong lượt này. Khi có migration mới trong artifact, startup Backend bình thường có thể tự chạy migration.
- Chỉ xác minh **model/source**, chưa xác minh dữ liệu MySQL. Không suy permission/role_permission đang rỗng, ADMIN còn active hoặc V1 đã applied từ việc source có các bảng/seed.

## 2. Database mapping thực tế

| Bảng | Cột liên quan | Ràng buộc / hành vi |
|---|---|---|
| app_role | id BIGINT UNSIGNED AUTO_INCREMENT; code VARCHAR(50), name VARCHAR(100), description VARCHAR(255) NULL; created_at/updated_at DATETIME(6) NOT NULL | PK pk_app_role; UNIQUE uq_app_role_code(code); code dùng utf8mb4_0900_bin. |
| permission | id BIGINT UNSIGNED AUTO_INCREMENT; code VARCHAR(100), name VARCHAR(120), description VARCHAR(255) NULL; created_at/updated_at DATETIME(6) NOT NULL | PK pk_permission; UNIQUE uq_permission_code(code); không có cột status/approved/is_active. |
| role_permission | id BIGINT UNSIGNED AUTO_INCREMENT; role_id, permission_id NOT NULL; created_at DATETIME(6) NOT NULL | UNIQUE uq_role_permission_role_permission(role_id,permission_id); index ix_role_permission_permission; hai FK tới app_role/permission, ON DELETE RESTRICT ON UPDATE RESTRICT. |
| user_role | id BIGINT UNSIGNED AUTO_INCREMENT; user_id, role_id NOT NULL; assigned_at DATETIME(6) NOT NULL | UNIQUE uq_user_role_user_role(user_id,role_id); hai FK tới app_user/app_role, DELETE/UPDATE RESTRICT. Không UNIQUE(role_id), hỗ trợ nhiều ADMIN. |

Tất cả bảng trên dùng InnoDB, mặc định utf8mb4/utf8mb4_vi_0900_ai_ci; code role/permission override binary collation. Code normalization của design là uppercase ASCII với ký tự cho phép; catalog hiện dùng tập con `[A-Z0-9_]`, tối đa 100. Không sửa code không canonical trong dữ liệu cũ bằng cách tự trim/uppercase rồi ghi đè.

V1 hiện là migration duy nhất trong thư mục source; seed đúng ADMIN, MANAGER, SALES_STAFF, WAREHOUSE_STAFF bằng INSERT code/name/timestamps CURRENT_TIMESTAMP(6). Không seed permission, role_permission, user hoặc mật khẩu. Không giả định các role có ID 1–4; mọi grant phải lookup bằng code và lấy ID thực tế. Không sửa V1 đã được áp dụng.

Resolver đọc `UserRole -> AppRole -> RolePermission -> Permission.code`, union các role V1 thực tế sau kiểm tra actor active. Chỉ có permission row không cấp quyền. PermissionPolicy còn kiểm tra catalog eligibility; raw AuthorizationService không có catalog gate. Vì DB không có cột status, việc giữ 9 code DENY phải tiếp tục do policy và kiểm soát manifest, không dựa vào một cờ DB giả định.

## 3. Hai manifest và hai cổng phát hành riêng

Tài liệu này không phải approval artifact. Không cần đợi TV2 hoàn thành module để review; **Stage B bắt buộc có xác nhận ma trận/cặp grant của cả TV1 và TV2** trước phát hành.

| Giai đoạn | Manifest bất biến sau review | Phạm vi ghi được đề xuất | Không được làm |
|---|---|---|---|
| A — Register catalog | Danh sách code được phép đăng ký, name/description, version, checksum/hash và bằng chứng phê duyệt đăng ký | Chỉ INSERT permission còn thiếu | Không role grants, không user_role/app_user, không role mới, không mở API. |
| B — Assign grants | Danh sách cặp (role_code,permission_code) được hai thành viên xác nhận, phạm vi quản lý, dependencies, version/hash và kế hoạch recovery | Chỉ INSERT role_permission còn thiếu đúng các cặp đã duyệt | Không sinh từ mọi ô P, không wildcard ADMIN, không overwrite/delete mapping khác. |

Đề xuất A đăng ký **tập con được duyệt trong 51 ELIGIBLE_PROPOSED**, tối đa 51 ở baseline này; không tự động cho cả 51 vào migration. Metadata ban đầu đơn giản có thể là name = code, description = NULL, nhưng cũng phải chốt trước khi đóng băng manifest. Có thể giữ đầy đủ 60 tên trong enum mà DB chỉ có tập đã được đăng ký.

B chỉ nhận cặp đã duyệt có permission nằm trong A hoặc permission tương thích đã tồn tại và được nhận diện rõ. Ma trận hiện có **68 ô P** (ADMIN 7, MANAGER 27, SALES_STAFF 18, WAREHOUSE_STAFF 16) là danh sách ứng viên, **không phải 68 grants đã được phép phát hành**. Mỗi cặp còn phải đủ action/scope/transaction contract; mapping không mở use case thiếu ownership/state.

Cả A và B đều loại trừ chín code:

```text
ROLE_READ
PERMISSION_READ
CUSTOMER_DEACTIVATE
ADJUSTMENT_REJECT
ADJUSTMENT_CANCEL
SALE_COMPLETE
PAYMENT_VOID
REFUND_READ
REFUND_CREATE
```

Code hiện hữu thuộc nhóm này không bị xóa ngầm. Nếu đã có grant tới H/unknown, dừng rollout để review/reconcile riêng; không dùng “policy sẽ chặn” thay cho kiểm kê mapping. Không tự chuyển H sang eligible bằng SQL. Chưa có approval thì giữ nguyên DB; thiết kế chưa phải giấy phép seed.

## 4. Phương án migration đề xuất

### 4.1. Versioned, frozen payload và kiểm tra trong cùng transaction

Đề xuất **hai Java-based versioned data migrations của Flyway**, dùng JDBC có sẵn, chỉ DML trên các bảng InnoDB hiện có. Lý do: cần pre/post assertions, so metadata, phát hiện role/permission thiếu và fail hard trong cùng transaction; không dùng SQL INSERT SELECT trả 0 dòng như bằng chứng migration đã đạt mục tiêu.

Đây là lựa chọn kỹ thuật PROPOSED khác với V1 SQL; cần duyệt runner/checksum/discovery trước implementation. Không tạo class migration trong lượt này.

- Ví dụ tên tương lai: `V2__register_permission_catalog` và `V3__grant_approved_role_permissions`, **chỉ nếu version còn trống khi triển khai**. TV1/TV2 phải phối hợp version trước commit; không dùng SQL và Java cùng một version. Không tạo V2/V3 hoặc tự đặt target trong task này.
- Dự kiến Java package `db.migration`, classpath location theo cấu hình hiện có; phải kiểm thử discovery/đóng gói thực tế trước deploy. Không dùng Spring component/startup runner, Bootstrap, JPA saveAll hoặc enum scan để tự seed.
- Payload của từng migration là danh sách literal/resource đã review, đóng băng theo version; không duyệt `PermissionCode.values()` hoặc ma trận Markdown “mới nhất” lúc chạy. Enum thay đổi sau này không làm migration cũ cấp thêm quyền.
- Java migration cần `getChecksum()` ổn định, được kiểm thử trên payload/SQL/contract revision bất biến; Java migrations không có checksum mặc định như SQL. CI còn kiểm tra fingerprint source/artifact vì checksum payload không tự phát hiện mọi thay đổi Java implementation. Sau applied không sửa migration cũ, dù checksum tình cờ không đổi.
- Yêu cầu `canExecuteInTransaction=true`, dùng connection do Flyway cung cấp, không tự close/commit/setAutoCommit/start transaction lồng. Trước rollout phải chứng minh DML rollback với driver/Flyway/MySQL thực tế.
- Không trộn CREATE/ALTER/TRUNCATE/procedure/temporary table, SQL GRANT/REVOKE của MySQL, hoặc sửa schema/history vào hai data migrations. MySQL có các lệnh implicit commit; không hứa rollback DDL như DML. Xem [MySQL implicit commits](https://dev.mysql.com/doc/refman/8.0/en/implicit-commit.html).
- Flyway quản lý transaction/history; lỗi trước commit phải rollback DML của stage đó. Không coi A và B là một transaction xuyên deployment. Cần kiểm tra riêng trạng thái schema history khi có lỗi/kết quả commit không rõ. [Flyway transaction handling](https://documentation.red-gate.com/fd/migration-transaction-handling-273973399.html).
- Không dùng repeatable migration cho role grants, callback “sync every startup” hoặc upsert ghi đè để ép DB giống ma trận. Không cập nhật updated_at/created_at các row đã tồn tại.

### 4.2. SQL minh họa — chỉ trong tài liệu, KHÔNG thực thi

Các dấu ? là JDBC parameters từ manifest được duyệt, không từ client/env arbitrary data. Đây là template để review, **không phải migration hoàn chỉnh**: cần assertions, target check, transaction và kiểm kê ở mục 5.

Stage A, mỗi entry được duyệt; bind code/name/description/code:

```sql
INSERT INTO permission (code, name, description, created_at, updated_at)
SELECT ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
WHERE NOT EXISTS (
    SELECT 1 FROM permission existing WHERE existing.code = ?
);
```

Trước INSERT phải xác nhận code canonical, eligible theo release đã duyệt, code/name/description trong độ dài/nullability schema và không trùng manifest. Nếu code đã có: chỉ adopt/no-op khi semantics và metadata đã được review, name/description khớp manifest hoặc ngoại lệ adoption đã được đóng băng rõ. Giữ ID, timestamps và mappings hiện hữu. Mismatch, alias khác case/ASCII trim hoặc code dùng với nghĩa khác => dừng; không normalize/rename/overwrite.

Stage B, mỗi cặp đã duyệt; bind permission_code/role_code:

```sql
INSERT INTO role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, UTC_TIMESTAMP(6)
FROM app_role r
JOIN permission p ON p.code = ?
WHERE r.code = ?
  AND NOT EXISTS (
      SELECT 1 FROM role_permission existing
      WHERE existing.role_id = r.id AND existing.permission_id = p.id
  );
```

Migration phải assert mỗi code resolve đúng một role/permission trước khi chèn, so metadata/status và xác nhận pair hiện hữu sau cùng. INSERT 0 dòng chỉ hợp lệ nếu **đã có đúng pair**; role/permission thiếu không được coi là no-op thành công. Không SELECT mọi permission để CROSS JOIN role ADMIN, không INSERT IGNORE/REPLACE, không ON DUPLICATE KEY UPDATE hoặc bỏ FK checks. UNIQUE là bảo vệ cuối; conflict không dự kiến phải throw/rollback, không nuốt lỗi.

Timestamp mới đề xuất dùng UTC_TIMESTAMP(6) cho metadata mới; không sửa timestamps V1 hoặc dữ liệu cũ để “chuyển UTC”. Cần kiểm tra precision/timezone khi tích hợp, không khẳng định seed role lịch sử đã UTC.

## 5. Preflight, drift và assertions bắt buộc

Chỉ thực hiện trong nhiệm vụ vận hành sau khi có approval và cho phép kết nối.

1. Xác minh target từ cấu hình hiệu lực: JDBC MySQL, host local, database chính xác `computer_store_dev`; kiểm tra lại SELECT DATABASE() bằng connection migration. Không fallback sang computer_store hoặc tự create database. DB_* đủ nhưng không in giá trị/URL có secret.
2. Có backup/snapshot phục hồi đã xác minh; xác định người vận hành, artifact/hash, môi trường và điểm dừng. Maintenance window phải ngăn app/admin/manual writers thay mapping trong lúc kiểm kê/ghi; Flyway history lock không thay thế việc kiểm soát mọi writer.
3. Kiểm tra Flyway info/validate và migration locations/version/checksum đã applied đúng release. Schema có sẵn mà thiếu history, history fail/mismatch, version collision hoặc target sai => dừng, không tự baseline/repair/outOfOrder/ignore lỗi.
4. Kiểm tra schema metadata đúng V1: InnoDB, PK/UNIQUE/FK/RESTRICT, signedness/collation/độ dài; kiểm tra orphan links kể cả nếu trước đây có người tắt FK. Không sửa schema hoặc tạo role thiếu để làm migration pass.
5. Kiểm kê permissions và pairs hiện hữu theo code, phân biệt canonical/exact/alias/conflict. Các role V1 phải tồn tại đúng code; role khác ngoài manifest không bị chỉnh/xóa. Chặn H/unknown grants và drift chưa được review; không coi label tương tự là cùng semantics.
6. Chốt baseline protected inventory: permission IDs/metadata/timestamps, role_permission pair/ID/time, user_role và số lượng account/role assignments. Lưu dữ liệu cần thiết vào artifact vận hành có kiểm soát; log chung chỉ có version/count/hash/kết quả, không username/password/hash/session/DB credentials.
7. Stage A assert sau: mọi entry A resolve đúng một row; không thay row cũ; permission tăng đúng số thiếu; **role_permission, user_role, app_user không đổi**. Không assert tổng permission toàn DB = 51/60 vì có thể có code cũ ngoài scope.
8. Stage B assert sau: tất cả pair được duyệt tồn tại duy nhất, tăng đúng số thiếu; pairs/metadata ngoài insert set không đổi; permission, app_role, user_role, app_user không đổi. Ghi riêng pair có sẵn được adopt và pair vừa thêm; không chỉ ghi tổng count.
9. Giữ insert set/snapshot trước-sau và version checksum làm bằng chứng recovery. Nếu kiểm tra sau thất bại, throw trước commit. Sau commit kiểm tra lại từ connection mới trước khi phục vụ traffic.

### Phân loại mapping được thêm thủ công

| Hiện trạng | Cách xử lý đề xuất |
|---|---|
| Permission code/metadata khớp đã review | Adopt/no-op, giữ ID/time; không đánh dấu row cũ là do migration tạo. |
| Permission khớp code nhưng khác ý nghĩa/name/description, hoặc có alias không canonical | Dừng, cần quyết định adoption/điều chỉnh bằng task riêng; không ghi đè. |
| Pair đúng manifest B đã tồn tại | Adopt, giữ ID/created_at; không duplicate và không thuộc insert set để xóa khi rollback. |
| Pair thừa trong phạm vi quản lý của B | Dừng review; migration additive không đủ để reconcile quyền thừa, không xóa ngầm. |
| Pair ngoài scope B | Giữ nguyên, ghi nhận blast radius và cần xác nhận chấp nhận hiện trạng trước rollout; không sync toàn bộ role. |
| Grant H/unknown hoặc orphan FK | Dừng rollout, bảo toàn dữ liệu để điều tra; recovery/revoke phải có quyết định riêng. Policy vẫn DENY H qua đường mới. |
| Writers ngoài kế hoạch/lock timeout/UNIQUE race | Dừng/rollback stage; xác minh snapshot lại, không tự retry vô hạn hoặc bỏ qua conflict. |

B phải định nghĩa scope riêng, ví dụ các role/pairs nào được quản lý; approved insert list không tự cho phép thu hồi tất cả pair không xuất hiện trong list.

## 6. Thứ tự rollout và các trường hợp môi trường

1. Review manifest A và runner/transaction/checksum/discovery; xác nhận quyền triển khai target cụ thể. Đóng gói artifact A **không chứa migration B chưa được duyệt**.
2. Preflight/read-only inventory/backup và maintenance window; chọn runner vận hành riêng có externalized credentials. Đề xuất job dùng Flyway Java API với dependencies/version được kiểm thử; chưa có job trong repo. Runner phải tắt tự tạo schema/tự baseline/out-of-order và bật validation; cấu hình cụ thể cần review theo R05. Không giả định `mvn flyway:migrate` đã được POM cấu hình, hoặc CLI tự load được Java migrations trong Boot jar.
3. Chạy Stage A trong task được phép; xác minh postconditions từ connection mới. Dừng ở A nếu B chưa được hai thành viên xác nhận; catalog tồn tại vẫn không được cấp quyền.
4. TV1 — Trần Minh Nhựt và TV2 — Nguyễn Phạm Gia Huy xác nhận đúng version ma trận/cặp grant, scopes, H exclusion và blast radius; đóng gói B bằng artifact mới, không chỉnh A đã applied.
5. Kiểm kê lại vì dữ liệu có thể thay đổi giữa hai stage. Stage B có transaction riêng; khóa các role liên quan theo thứ tự cố định bằng từng lookup code dưới lock nếu runner cần, đồng thời giữ maintenance window. Không dựa vào SELECT COUNT rồi ghi để chống mọi race.
6. Sau B kiểm tra pairs, invariants và policy; role grants có thể được resolver thấy ở lần gọi tiếp theo, không cần login lại để làm chúng “active”. Giữ API nghiệp vụ denyAll; mở từng endpoint/Service guard là task khác.
7. Startup Backend với Flyway disabled sau job migration thành công là phương án vận hành PROPOSED để tránh chạy pending migrations ngoài window; không sửa properties ở task này. Tắt Flyway không thu hồi grants và không thay thế kiểm tra dữ liệu.

| Tình huống | Điều kiện / kết quả kỳ vọng |
|---|---|
| Fresh database/schema hợp lệ được tạo sẵn trong môi trường được phép | V1 tạo schema objects và bốn role trước A. A/B không tạo schema/account; B vẫn cần approval dù fresh. V1 DDL lỗi không được giải quyết bằng giả định có thể rollback toàn bộ. |
| computer_store_dev đã có ADMIN | Kiểm tra history/schema và snapshot; không gọi Bootstrap, không sửa user/password/user_role. Chỉ thêm catalog/pairs được duyệt, ADMIN vẫn phải có grant cụ thể. |
| Có nhiều ADMIN | Tất cả share role ADMIN nhưng user_id riêng. Grant ADMIN áp dụng cho mọi account có role đó, kể cả account sẽ được tạo sau này; cần review toàn bộ blast radius, không gán riêng cho “Admin đầu tiên”. |
| A applied thành công, B chưa có hoặc thất bại | A được giữ; không tự chạy B hoặc xóa catalog. B rollback các insert chưa commit, không rollback A đã commit. |
| A/B đã applied, migrate lại đúng release | Flyway không chạy lại version đã thành công. Không coi đó là reconciler tự tạo lại pair bị xóa thủ công sau này. Drift cần phát hiện/review và migration mới. |
| Existing schema không có history khớp V1 | Dừng, đánh giá adoption/baseline riêng; không bật baselineOnMigrate hoặc sửa history để bỏ qua. |
| ADMIN đều inactive / chưa có ADMIN | A/B không phục hồi hoặc tạo user. Việc bootstrap đầu tiên/recovery chỉ thực hiện bằng task riêng phù hợp contract, không nằm trong rollout grants. |

Schema hiện có đã hỗ trợ nhiều ADMIN; User Management và last-active-ADMIN guard của D13 chưa triển khai. Bootstrap chỉ tạo ADMIN đầu tiên, từ chối khi có ADMIN assignment kể cả inactive. Role grants không làm thay đổi số ADMIN active và không triển khai bảo vệ tài khoản cuối.

## 7. Idempotency, partial failure và recovery

- **Data idempotency:** chạy lại logic A/B trên cùng approved manifest và cùng dữ liệu đã xác minh phải không tạo duplicate, không rewrite timestamps/IDs hoặc grants có sẵn. UNIQUE code/pair + conditional insert + assertions hỗ trợ điều này. Không hứa AUTO_INCREMENT liên tục sau rollback/race.
- **Migration idempotency:** Flyway áp version thành công một lần. Không sửa checksum/payload version cũ để seed lại; correction/add/revoke được review qua version mới. [Flyway versioned migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations).
- **Lỗi trước commit DML:** rollback stage, kiểm tra actual rows/history; không suy migration được rollback hoàn toàn chỉ từ exit code. Thiết kế không trộn DDL nên hướng tới rollback toàn bộ insert stage, phải kiểm chứng trên MySQL.
- **Commit outcome không rõ:** mất kết nối/timeout sau commit => đóng traffic, inspect history và insert set từ connection mới; không tự chạy lại hoặc xóa rows theo phỏng đoán.
- **A thành công, B lỗi:** giữ A; recovery B không được xóa permission A. Nếu có history failed entry, xác minh/cleanup dữ liệu thật trước, chỉ dùng repair khi có task và quyết định rõ. Repair sửa metadata history, không tự hoàn tác dữ liệu người dùng. [Flyway repair](https://documentation.red-gate.com/flyway/reference/commands/repair).
- **B thành công, deploy/verification thất bại:** chặn use cases/traffic trước. Rollback application không rollback role_permission. Không rollback xuống bản trước policy DENY của Part 3B-2A; artifact quay lại phải được kiểm thử vẫn giữ denyAll và provisional guard.
- **Compensating migration:** ưu tiên forward fix. Nếu phải thu hồi, chỉ những pairs chứng minh được vừa thêm bởi B, đúng approved recovery scope và không thay đổi sau đó; đối chiếu before/after IDs và trạng thái hiện tại trong window. Không xóa pair manual/adopted, không DELETE theo toàn bộ role hoặc mọi code trong manifest. Không cung cấp lệnh xóa hàng loạt hoặc SQL destructive ở đây.
- **Catalog rollback:** mặc định giữ permission rows chưa dùng; không xóa parent gây FK RESTRICT hoặc mất mapping ngoài scope. Nếu phải xóa row do A tạo, cần task/approval riêng chứng minh ownership và không còn references.
- Không dùng Flyway clean/reset, không tự repair checksum cho một migration bị sửa, không tắt FK, không restore đè live DB mà chưa đánh giá dữ liệu phát sinh sau backup. Backup/restore là phương án cuối được kiểm thử, cần task vận hành riêng; không thực hiện lượt này.
- Không cần hoặc giả định Flyway Undo/licensed features. Append-only approved data changes đủ cho hướng triển khai đề xuất.

## 8. Test plan rollout — snapshot Part 3B-2B.1

| Nhóm | Cases / assertions cần có |
|---|---|
| Manifest/isolated | Exact code/case/unique; A subset của 51 eligible, không H/unknown; B unique pairs, role V1, approval version, permission đã đăng ký; metadata length/null; reject wildcard/ADMIN all-code expansion. |
| Migration/JDBC isolated | Parameter binding đúng; thiếu role/permission => throw; mismatch/adoption conflict => throw; 0 insert chỉ hợp lệ khi row/pair đã tồn tại; không gọi JPA/Bootstrap/user_role writes; checksum/payload bất biến. |
| Policy regression | 220 tests hiện có tiếp tục PASS; cả 9 H vẫn 403 khi mapping nhầm; unknown 403, anonymous/inactive 401, không role-bypass/default grants; API denyAll/login/logout/CSRF giữ nguyên. |
| MySQL 8.0.46 + Flyway 12.4.0 hoặc version deploy đã xác minh | Instance thử nghiệm tách biệt được task sau cho phép, schema computer_store_dev; fresh V1→A, rồi B được duyệt. Không dùng H2 để chứng minh collation/FK/transaction MySQL. |
| Existing database fixtures | Permission khớp/mismatch/alias; manual pair approved/unapproved/outside scope; ADMIN một/nhiều/inactive; orphan links; user_role/app_user snapshots giữ nguyên. |
| Idempotency/drift | Chạy logic hai lần không đổi ID/time; migrate lại version applied không chạy lại; xóa pair thủ công sau applied phải được phát hiện, không tự reconcile. |
| Failure injection | Lỗi sau insert thứ k, trước postcheck, ngay trước/sau commit; A commit/B fail; metadata/history failed/unknown; DML rollback và các rows adopted giữ nguyên. |
| Concurrent rollout | Hai migrator, manual writer, UNIQUE conflict/lock timeout; exclusive window và history/role locks không cho mất snapshot; không tự nuốt lỗi rồi thành công. |
| Packaging/deployment/recovery | Java migration discovery trong artifact thật, deterministic checksum, runner đúng classpath/location/version; chỉ A được ship khi B chưa duyệt; forward recovery không xóa grants preexisting. |

Không thêm test framework/dependency, không tạo database/container hoặc chạy tests DB trong Part 3B-2B.1/2. Kết quả isolated mới tại mục 12: 249 PASS; các cases migration/JDBC/MySQL/discovery/recovery vẫn chỉ là test plan, chưa phải PASS của migration chưa tồn tại.

## 9. Runtime verification plan — SQL chỉ để review, CHƯA chạy

Các truy vấn dưới đây chỉ dùng trong task có quyền kết nối. Kiểm tra target/config/backup trước khi chạy; không query password_hash, password, session hoặc credential variables.

```sql
SELECT DATABASE();

SELECT code, name FROM app_role ORDER BY code;

SELECT code, name, description, created_at, updated_at
FROM permission ORDER BY code;

SELECT rp.id, r.code AS role_code, p.code AS permission_code, rp.created_at
FROM role_permission rp
JOIN app_role r ON r.id = rp.role_id
JOIN permission p ON p.id = rp.permission_id
ORDER BY r.code, p.code;

SELECT r.code, COUNT(DISTINCT ur.user_id) AS assigned_users,
       COUNT(DISTINCT CASE WHEN u.is_active = 1 THEN u.id END) AS active_users
FROM app_role r
LEFT JOIN user_role ur ON ur.role_id = r.id
LEFT JOIN app_user u ON u.id = ur.user_id
GROUP BY r.code;

SELECT version, description, type, script, checksum, success
FROM flyway_schema_history ORDER BY installed_rank;
```

Trước truy vấn history phải xác nhận bảng/default history location thực sự tồn tại; thiếu hoặc cấu hình override thì dừng đánh giá. Các INNER JOIN không tự phát hiện orphan; còn cần FK metadata/orphan checks như mục 5. So tập code/pair với manifest và protected snapshot, không chỉ count tổng. Không xuất dữ liệu kiểm kê ra public logs.

Sau A: xác minh catalog chỉ có entry thiếu được thêm, role grants/account links không đổi. Sau B: xác minh toàn bộ exact approved pairs, không duplicate/H mới, ngoài scope giữ nguyên. Kiểm tra raw resolver không cache quyền, policy vẫn chặn H; không thử grant H lên local live DB chỉ để test.

API health/csrf và anonymous me có thể được kiểm tra trong task startup được phép; xác minh authenticated business request vẫn 403 dưới denyAll. Không POST login bằng account đoán hoặc thực hiện transaction nghiệp vụ để “test grant”; positive policy verification dùng fixture trên instance test hoặc task có tài khoản thực và phạm vi rõ. Runtime verification không phải giấy phép mở endpoint.

## 10. R01–R07 — phương án kỹ thuật tại Part 3B-2B.2

Mã R04–R07 được sắp lại theo yêu cầu Part 3B-2B.2: R04 cũ (runner) chuyển sang R05; R05 cũ (inventory) sang R06; transaction/failure của R06 cũ nằm trong R03/R07; các việc API/ownership/session của R07 cũ vẫn là dependencies ngoài rollout. Không đổi tracker task IDs hay D01–D13.

| Mã | Phương án cụ thể | Căn cứ kỹ thuật | Rủi ro / điểm dừng | Trạng thái |
|---|---|---|---|---|
| R01 — Stage A manifest | Chỉ một subset được chỉ định rõ trong 51 eligible, entry có code/name/description; không có default manifest, không điền đủ 51. Đề xuất name=code/description=NULL nếu được review; helper không tự điền metadata. | PermissionCode hiện có; permission VARCHAR(100/120/255), description nullable; validate/list snapshot đã triển khai tại mục 12. | Validation thành công không là approval. Chưa có danh sách/metadata/adoption exceptions và bằng chứng phê duyệt gắn hash, nên chưa tạo executable migration. | Helper IMPLEMENTED; manifest/approval PENDING |
| R02 — Stage B grants | Hoãn Stage B; exact pairs/scopes phải được TV1 và TV2 xác nhận trên version ma trận cụ thể, không H, wildcard hoặc sync mọi ô P. | role_permission UNIQUE pair và resolver union role server; Stage A không thay mapping. | Grant áp dụng cho mọi user cùng role, gồm nhiều ADMIN; 68 ô P không phải approval; không được dùng approval A thay B. | PROPOSED / PENDING; chưa triển khai |
| R03 — SQL hay Java migration | Đề xuất Java-based versioned DML qua JDBC để pre/post assertions và insert-only/adopt trong một transaction; tách A/B. Chỉ tạo ở nhiệm vụ sau approval; helper hiện tại không triển khai JavaMigration. | V1 là SQL/InnoDB; assertions zero-row/mismatch cần fail hard, không dựa INSERT SELECT 0 rows; Flyway Java API có sẵn trong dependencies. | SQL-only đơn giản nhưng dễ thiếu assertions; Java cần discovery/checksum/transaction tests thật. Không trộn DDL hoặc dùng helper để chạy khi startup. | PROPOSED; implementation migration PENDING |
| R04 — Checksum/version | Fingerprint SHA-256 nội dung manifest format V1 đã triển khai, thứ tự entries chuẩn hóa theo code. Approval tương lai phải gắn release/revision và hash exact payload. Migration version chọn lúc tích hợp để không trùng TV2; checksum Flyway riêng trên frozen payload/SQL/validation revision, kèm CI artifact fingerprint. | Thuật toán tại mục 12 có golden vector độc lập. Java migration không có checksum mặc định; getChecksum tương lai không được là giá trị bỏ trống hoặc chỉ hardcode để che thay đổi. | contentSha256 không phải chữ ký/approval hoặc Flyway checksum. Không để enum/helper phiên bản mới thay đổi nội dung hay replay migration cũ; payload/Java implementation applied phải bất biến. | SHA-256 helper IMPLEMENTED; Flyway version/checksum PROPOSED/PENDING |
| R05 — Runner/deployment | Đề xuất job Flyway Java API riêng, credentials externalized, target guard local computer_store_dev, validation bật, không tự create schema/baseline/out-of-order; artifact A không chứa B chưa duyệt. Startup app giữ Flyway disabled ngoài window qua cấu hình vận hành được duyệt. | application.properties đang enabled=true và classpath:db/migration; POM không có Flyway Maven plugin/runner. | Đóng gói sai có thể tự chạy khi Boot startup; job/commands/classpath/permission vận hành chưa có, không tự triển khai runner hoặc sửa properties lượt này. | PROPOSED / PENDING |
| R06 — Existing DB inventory | Dùng task read-only riêng kiểm tra target/history/schema/code/pairs/orphans, snapshots và adoption exceptions như mục 5/9; giữ rows ngoài scope. | FK/UNIQUE/collation V1; source không chứng minh trạng thái DB thực. | Chưa kết nối nên chưa biết conflicts/manual grants/ADMIN state; không giả DB rỗng, không tự repair/overwrite để vượt blocker. | PENDING — chưa xác minh |
| R07 — Recovery | Rollback DML stage chưa commit, giữ A nếu B fail; outcome unknown thì inspect. Ưu tiên forward fix, chỉ thu hồi insert set có bằng chứng bằng migration/task được duyệt; giữ adopted rows, backup là phương án cuối đã thử. | InnoDB và giới hạn implicit commit; Flyway history/checksum riêng dữ liệu; app rollback không rollback grants. | Chưa chứng minh transaction/history/backup/recovery trên MySQL; không xóa hàng loạt, repair tự động hoặc coi isolated PASS là runtime recovery PASS. | PROPOSED / PENDING |

Không mục nào tự được ghi APPROVED bởi nhiệm vụ này. IMPLEMENTED chỉ áp dụng helper/tests kỹ thuật, không phải approval nghiệp vụ D01–D13 hoặc deployment. R01–R07 là mã nội bộ tài liệu, không thêm task IDs.

**Blocker cụ thể để tạo migration Stage A:** thiếu manifest đăng ký cụ thể và metadata/adoption policy được phê duyệt, kèm release/revision/hash và bằng chứng review. Sau đó vẫn cần chốt R03/R04/R05 và verification R06/R07 trước khi chạy DB. Hiện không có executable migration hoặc approved manifest artifact; không lấy fixtures trong tests làm seed.

Tham khảo Java migration/checksum: [Flyway Java-based migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/java-based-migrations). Nguồn kỹ thuật không phê duyệt ma trận CSM hoặc thay thế kiểm chứng runtime.

## 11. Phạm vi Part 3B-2B.1 — snapshot trước implementation helper

Chỉ tạo thiết kế này và cập nhật ghi chú/lịch sử IAM-002/006 nếu cần. Giữ 33 task IDs, 8 dependencies, owner và mọi trạng thái TV2; không đánh dấu IAM-002/004/006 DONE. Không sửa source Backend/Frontend, Entity/Repository, properties/POM hoặc Bootstrap; không tạo migration/seed/grants/accounts, kết nối MySQL, chạy Flyway, commit/push hoặc merge.

Tài liệu không chứa credentials. SQL chỉ là proposed templates/read-only verification plan, chưa được thực thi. Chưa có seed migration hoặc runner mới để chạy; dừng sau Part 3B-2B.1.

## 12. Part 3B-2B.2 — manifest preparation helper, local chưa commit/push

### 12.1. API và validation đã triển khai

Class [PermissionCatalogManifest](../backend/src/main/java/com/computerstore/computer_store_backend/identity/authorization/registration/PermissionCatalogManifest.java) ở identity.authorization.registration là value/helper thuần Java, không Spring component, Flyway migration, startup runner, repository, datasource hoặc SQL executor.

```java
// Minh họa cách gọi helper, không phải manifest đã được phê duyệt hoặc lệnh seed.
var proposed = PermissionCatalogManifest.validate(List.of(
    new PermissionCatalogManifest.Entry("PRODUCT_READ", "Proposed product lookup", null)
));
var snapshot = proposed.entries();
var fingerprint = proposed.contentSha256();
```

- Caller phải cung cấp danh sách cụ thể; null/empty manifest và null entry bị từ chối. Không có method tự lấy toàn bộ eligible codes, default manifest, file/resource seed hoặc trạng thái APPROVED.
- Code phải exact ASCII [A-Z0-9_], 1–100 ký tự, nằm trong PermissionCode hiện tại và có ELIGIBLE_PROPOSED. Cấm unknown, cả 9 PROVISIONAL_DENY, duplicate dù metadata giống hay khác. Không trim/đổi case hoặc normalize code sai thành code hợp lệ.
- name bắt buộc, không blank, tối đa 120 Unicode code points; description nullable, tối đa 255. Cả hai phải là Unicode hợp lệ có thể biểu diễn UTF-8, tính supplementary character là một ký tự để phù hợp utf8mb4/VARCHAR; không tự sửa/cắt metadata.
- Snapshot sắp theo code, sao chép và immutable; không sửa list của caller, không giữ mutable list. Exception không echo input payload.
- Validation không đọc DB hoặc so metadata existing rows. Adoption/history/target checks vẫn phải làm riêng khi có task cho phép DB. Không thể dùng một giá trị đã validate như bằng chứng đã seed/cấp role.
- Helper không thay PermissionCode/PermissionPolicy/AuthorizationService, không thay 401/403, không thêm grants/account hoặc business endpoint. Last-active-ADMIN guard chưa triển khai; Stage B chưa triển khai.

### 12.2. Content fingerprint format V1

SHA-256 do JDK cung cấp, không dependency mới; output là 64 ký tự hex lowercase. Dữ liệu canonical gồm:

1. Chuỗi ASCII domain "CSM_PERMISSION_CATALOG_MANIFEST_V1", mã hóa theo field format dưới đây.
2. Số entry int32 big-endian.
3. Entries sort tăng dần theo code; mỗi entry ghi lần lượt code, name, description.
4. Field khác null: độ dài **byte UTF-8** int32 big-endian, sau đó exact bytes; null: độ dài -1, không payload.

Thứ tự input không đổi hash; đổi code/name/description/tập entries phải đổi hash. NULL khác empty string; length prefixes ngăn field/delimiter ambiguity. Không chuẩn hóa Unicode/newlines/metadata làm nội dung khác nhau có cùng canonical payload.

Đây chỉ là content identity; không xác minh người phê duyệt, target DB hoặc rollout authorization. Không triển khai getChecksum()/JavaMigration, không chọn V2/V3 và không tạo job Flyway.

Helper kiểm tra enum/status **của release đang chuẩn bị**. Migration lịch sử trong tương lai phải dùng payload/logic đã đóng băng theo version; không đọc lại enum/helper “mới nhất” để tự thay đổi registration khi replay trên fresh DB. Thiết kế checksum/discovery/runtime của migration vẫn thuộc R03/R04/R05.

### 12.3. Kết quả tests và phần chưa kiểm chứng

[PermissionCatalogManifestTest](../backend/src/test/java/com/computerstore/computer_store_backend/identity/authorization/registration/PermissionCatalogManifestTest.java): **29/29 PASS**, bao gồm chín provisional parameterized cases; duplicates/unknown/invalid/oversized/empty/null, metadata boundaries/Unicode, immutable snapshot, SHA-256 golden vector/order/change/null boundaries, không tự grant cho ADMIN và không có bean migration/account/grant/datasource trong context chỉ scan package helper.

Chạy cùng toàn bộ **220 regression tests cũ**: **249/249 PASS**, 0 failure/error/skipped; Maven test/compile PASS, chọn tường minh 22 lớp isolated tests và loại ComputerStoreBackendApplicationTests. Context mới chỉ scan package helper, không Boot auto-configuration/datasource. Không dùng MySQL, chạy Flyway hoặc bootstrap thật.

Lệnh chạy riêng tests mới từ backend:

```powershell
.\mvnw.cmd -o "-Dtest=PermissionCatalogManifestTest" test
```

Chưa có tests thực thi migration/SQL/JDBC registration, MySQL adoption/transactions, discovery, rollout hoặc recovery vì chưa có migration/runner và chưa được phép kết nối. Các case này ở mục 8 vẫn PENDING.

### 12.4. Trạng thái vận hành

**Stage A chưa chạy; không có manifest APPROVED hoặc executable migration. Stage B chưa triển khai, không grants mới.** Business APIs vẫn denyAll; auth/CSRF, schema, Entity/Repository, properties/POM và Bootstrap giữ nguyên. Không tạo ADMIN hoặc sửa user_role. Task này chỉ thêm helper/test và cập nhật rollout/tracker; chưa commit/push, merge main hoặc phê duyệt D01–D13.
