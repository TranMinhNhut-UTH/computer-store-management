# FND-008 — Inventory Operations Contract

> **Trạng thái:** DRAFT để nhóm xét duyệt. Soạn bởi Thành viên 2 (owner Inventory Foundation),
> dựa trên `project-scope.md`, `requirements.md`, `database-design.md` (đặc biệt mục 4.2, 4.5, 5, 7),
> `backend-conventions.md` (mục F, G, H và P08) và mã nguồn thực tế: `V1__create_initial_schema.sql`,
> `application.properties`, các Entity trong package `inventory`, `catalog`, `identity`.
> Việc soạn tài liệu này KHÔNG đồng nghĩa các mục PROPOSED/PENDING đã được duyệt. Mọi PENDING phải
> được nhóm chốt trước khi triển khai use case phụ thuộc (theo đúng nguyên tắc P0 trong
> `backend-conventions.md` mục 3).

## 0. Phạm vi tài liệu

Tài liệu định nghĩa **hợp đồng nội bộ** của `InventoryOperationsService` — đầu mối duy nhất
ghi nhận biến động tồn kho (`inventory.quantity_on_hand` + `inventory_transaction`) cho mọi
module (Purchasing, Sales, Return, Adjustment). Không có module nào khác được tự ý
UPDATE `inventory` hoặc INSERT `inventory_transaction` ngoài service này.

Tài liệu **không** thay đổi schema, Entity, hay tạo migration mới.

## 1. Năm operation (bám theo `InventoryTransactionType` enum đã có trong code)

| Operation | Transaction type | Nguồn gọi | Source FK | Dấu delta |
|---|---|---|---|---|
| `applyPurchaseIn` | `PURCHASE_IN` | `PurchaseService.confirm()` | `purchase_item_id` | dương |
| `applySaleOut` | `SALE_OUT` | `SalesOrderService.confirm()` | `sales_order_item_id` | âm |
| `applyReturnIn` | `RETURN_IN` | `SalesReturnService.confirm()` | `sales_return_item_id` | dương (chỉ khi `restock_quantity > 0`) |
| `applyAdjustment` | `ADJUSTMENT_IN` / `ADJUSTMENT_OUT` | `InventoryAdjustmentService.approve()` | `adjustment_item_id` | theo dấu `quantity_delta` của dòng |
| `applySaleCancel` | `SALE_CANCEL_IN` | `SalesOrderService.cancel()` (chỉ khi đang `CONFIRMED`, chưa `COMPLETED`) | không có (dùng `reversal_of_transaction_id`) | dương, đảo đúng trị tuyệt đối `SALE_OUT` gốc |

Mỗi operation nhận **đã có sẵn** entity nguồn (`PurchaseItem`, `SalesOrderItem`...) do Service
gọi fetch trước — `InventoryOperationsService` không tự query lại từ ID để tránh N+1 và vì các
association trong code là LAZY, không cascade.

## 2. Input / Output / Error — từng operation

### 2.1. `applyPurchaseIn(PurchaseItem item, AppUser actor, LocalDateTime occurredAt)`

- **Input:** `item` đã thuộc 1 `Purchase` đang ở status `DRAFT` (Service gọi kiểm tra trước khi
  gọi vào đây); `actor` lấy từ current principal (DEP-002); `occurredAt` lấy từ UTC clock dùng
  chung (DEP-004, đang PENDING theo P06).
- **Precondition bên trong:**
  1. Khóa dòng `inventory` của `item.product` (`SELECT ... FOR UPDATE`).
  2. Kiểm tra SKU không bị chặn bởi stocktake `IN_PROGRESS` chưa đếm hoặc adjustment `PENDING`
     liên kết (database-design.md §4.5).
  3. Kiểm tra chưa tồn tại `InventoryTransaction` nào có `purchase_item_id = item.id`
     (idempotency — xem mục 4).
- **Side effect:** `quantity_on_hand += item.quantity`; insert 1 `InventoryTransaction`
  (`PURCHASE_IN`, `quantity_delta = +item.quantity`, `quantity_after` = số dư sau update).
- **Output:** `InventoryTransaction` đã lưu.
- **Error:** `SkuLockedByStocktakeException` (409), `DuplicateInventoryOperationException` (xem mục 4,
  thường không throw ra ngoài mà trả lại bản ghi cũ).

### 2.2. `applySaleOut(List<SalesOrderItem> items, AppUser actor, LocalDateTime occurredAt)`

- **Input:** toàn bộ dòng của 1 `SalesOrder` đang `DRAFT`, chuẩn bị `CONFIRMED`.
- **Precondition — theo đúng database-design.md §4.2 và §5:**
  1. Khóa **toàn bộ** dòng `inventory` liên quan, theo thứ tự `product_id` **tăng dần**
     (không theo thứ tự xuất hiện trong đơn) để tránh deadlock khi 2 đơn tranh nhau nhiều SKU.
  2. Với mỗi dòng, kiểm tra SKU không bị khóa bởi stocktake/adjustment mở.
  3. **Kiểm tra đủ tồn cho TẤT CẢ các dòng trước khi ghi bất kỳ transaction nào**
     (check-all-first). Nếu bất kỳ dòng nào thiếu → không insert gì cả, ném
     `InsufficientStockException` mang theo danh sách `(sku, requested, available)` của *mọi*
     dòng thiếu (không chỉ dòng đầu tiên) để Service trả lỗi rõ ràng 1 lần.
  4. Chỉ sau khi toàn bộ dòng pass bước 3, mới trừ tồn + insert `SALE_OUT` cho từng dòng,
     trong cùng transaction.
- **Side effect:** với mỗi dòng: `quantity_on_hand -= qty`; insert `InventoryTransaction`
  (`SALE_OUT`, `quantity_delta = -qty`).
- **Output:** `List<InventoryTransaction>`, 1 phần tử / dòng đơn.
- **Error:** `InsufficientStockException` (409 `INSUFFICIENT_STOCK`), `SkuLockedByStocktakeException` (409).

### 2.3. `applyReturnIn(SalesReturnItem item, CurrentActor actor, LocalDateTime occurredAt)`

- **Input:** `restockQuantity` đọc trực tiếp từ `item.getRestockQuantity()` — đây là field
  thật trên `sales_return_item` (CHECK `0 <= restock_quantity <= quantity`), KHÔNG nhận qua
  tham số riêng như thiết kế ban đầu, để tránh lệch giữa giá trị Service truyền và giá trị
  đã lưu trong DB.
- **Lấy product:** `item.getSalesOrderItem().getProduct()` — `SalesReturnItem` không có
  `product` trực tiếp.
- **Quy tắc đặc biệt:** nếu `restockQuantity <= 0` → ném `IllegalArgumentException` (lỗi lập
  trình của caller, không phải lỗi nghiệp vụ) — `SalesReturnService` phải tự kiểm tra trước,
  không được gọi vào đây khi `restockQuantity == 0`.

### 2.4. `applyAdjustment(InventoryAdjustmentItem item, AppUser actor, LocalDateTime occurredAt)`

- **Input:** `item` thuộc `InventoryAdjustment` **vừa được** chuyển sang `APPROVED`
  (Service gọi trong cùng transaction với việc set `approvedBy`/`approvedAt`, theo
  `ck_inventory_adjustment_transition`).
- **Precondition:**
  1. Khóa `inventory` của `item.product`.
  2. Kiểm tra `quantity_on_hand + item.quantityDelta >= 0` (delta âm không được làm tồn âm —
     database-design.md §7.7).
  3. Loại transaction suy ra từ dấu: `quantityDelta > 0` → `ADJUSTMENT_IN`, `< 0` →
     `ADJUSTMENT_OUT` (0 không hợp lệ, đã bị chặn bởi CHECK `ck_inventory_adjustment_item_delta`
     ở tầng Entity).
- **Side effect:** cập nhật số dư theo delta; insert 1 `InventoryTransaction`.
- **Output:** `InventoryTransaction`.
- **Lưu ý quan trọng:** đây là cách **duy nhất** để sửa số dư sai sau khi 1 phiếu nhập đã
  `CONFIRMED` (project-scope.md §6.2) — KHÔNG dùng `reversal_of_transaction_id` cho Purchase,
  vì CHECK `ck_inventory_transaction_source` chỉ cho phép reversal khi
  `transaction_type = 'SALE_CANCEL_IN'`.

### 2.5. `applySaleCancel(InventoryTransaction originalSaleOut, String reversalReason, AppUser actor, LocalDateTime occurredAt)`

- **Input:** gọi 1 lần / dòng `SALE_OUT` gốc của đơn đang hủy (Service lặp qua từng dòng của
  đơn, không gộp thành 1 lệnh nhiều dòng).
- **Precondition:**
  1. `originalSaleOut.transactionType == SALE_OUT` (không cho đảo 1 reversal, database-design.md §5:
     *"Không cho đảo movement đảo hoặc đảo lặp"*).
  2. Chưa có `InventoryTransaction` nào có `reversal_of_transaction_id = originalSaleOut.id`
     (UNIQUE constraint, idempotency).
  3. Khóa `inventory` của `originalSaleOut.product`.
- **Side effect:** `quantity_on_hand += |originalSaleOut.quantityDelta|`; insert
  `InventoryTransaction` (`SALE_CANCEL_IN`, `quantity_delta = +|originalSaleOut.quantityDelta|`,
  `reversal_of_transaction_id = originalSaleOut.id`, `reversal_reason` bắt buộc).
- **Output:** `InventoryTransaction`.
- **Phạm vi:** chỉ dùng khi hủy đơn `CONFIRMED` **trước bàn giao** (chưa `COMPLETED`) —
  Service phải chặn hủy nếu đã `COMPLETED` trước khi gọi vào đây.

## 3. Quyết định đã BASELINE (không tranh luận lại, chỉ tài liệu hóa)

Trích dẫn trực tiếp từ nguồn, áp dụng nguyên văn cho FND-008:

- **Lock order toàn hệ thống** (database-design.md §5): *"Khóa header/chứng từ trước, sau đó
  khóa các payment nếu có theo id tăng dần và inventory theo product_id tăng dần; mọi workflow
  tuân cùng thứ tự khóa."* → `InventoryOperationsService` luôn được gọi **sau khi** header đã bị
  khóa bởi Service gọi nó; bản thân nó chỉ chịu trách nhiệm khóa `inventory` theo `product_id` tăng dần.
- **Idempotency ở tầng ledger** (backend-conventions.md §H): UNIQUE trên source FK là
  *"bảo vệ cuối, không thay thế status/lock/business validation"* — Service gọi (Purchase/Sales/...)
  phải tự kiểm tra status chứng từ dưới khóa header **trước** khi gọi vào
  `InventoryOperationsService`; operation ở đây chỉ là lớp bảo vệ thứ hai.
- **`inventory.version` không thay thế row lock** (database-design.md §5, backend-conventions.md §G):
  optimistic locking chỉ phát hiện xung đột, không đảm bảo đủ tồn hay chống confirm trùng.
  `InventoryOperationsService` dùng pessimistic write lock (`SELECT ... FOR UPDATE`) là cơ chế
  chính; `version` do Hibernate tự quản lý qua managed Entity, Service **không** tự tăng, **không**
  dùng bulk/native update.
- **Stocktake/Adjustment blocking** (database-design.md §4.5): chặn ở đúng SKU đang mở, không
  khóa toàn kho; thực hiện bằng cách khóa `inventory` rồi kiểm tra tồn tại `stocktake_item` chưa
  đếm thuộc stocktake `IN_PROGRESS`, hoặc `inventory_adjustment_item` thuộc adjustment `PENDING`,
  cho cùng `product_id` — **không** cần thêm cột/bảng mới.
- **Reversal chỉ dành cho SALE_CANCEL_IN** — xác nhận bởi CHECK `ck_inventory_transaction_source`
  (đã copy nguyên vào `@Checks` trên `InventoryTransaction.java`) và database-design.md §7.8, §5.
- **Check-all-first cho Sales confirm** (database-design.md §7.2, §4.2): thiếu 1 SKU → rollback
  toàn đơn, không trừ tồn một phần.

## 4. Quyết định PROPOSED cần nhóm duyệt (soạn trong tài liệu này, chưa tự triển khai)

| # | Đề xuất | Lý do |
|---|---|---|
| P-A | `InventoryOperationsService` là 1 Spring bean duy nhất (không interface+Impl bắt buộc theo quy ước I), `@Transactional(propagation = REQUIRED)` ở method, tham gia transaction của Service gọi nó — không `REQUIRES_NEW` | Khớp backend-conventions.md §F: *"Không dùng REQUIRES_NEW cho movement"* |
| P-B | Row lifecycle của `inventory`: tạo kèm `quantity_on_hand = 0` **trong cùng transaction** tạo `Product` | Chủ dự án đã duyệt cho Product Create V1. `ProductService` sở hữu transaction và gọi `InventoryLifecycleService`. Xem backend-conventions.md §G. |
| P-C | Khi gặp `InventoryRowNotFoundException` (vi phạm invariant P-B) → ném lỗi integrity rõ ràng, **không** tự tạo ngầm | Tránh che giấu lỗi dữ liệu, đúng tinh thần backend-conventions.md §G |
| P-D | Mã lỗi nghiệp vụ: `INSUFFICIENT_STOCK`, `SKU_LOCKED_BY_STOCKTAKE`, `INVENTORY_ROW_NOT_FOUND` — theo đúng style `DOCUMENT_ALREADY_CONFIRMED` đã đề xuất ở §C | Đồng bộ error catalog toàn backend, chờ duyệt cùng P05 |
| P-E | Retry: không retry trong `InventoryOperationsService` — nếu có, thuộc lớp điều phối phía trên (Controller/Service gọi), ngoài phạm vi tài liệu này | Khớp backend-conventions.md §H, P09 còn PENDING |

## 5. PENDING — bắt buộc nhóm chốt trước khi code (không tự quyết trong tài liệu này)

Đối chiếu trực tiếp bảng P08 trong `backend-conventions.md`:

1. **Transaction owner chính xác**: `InventoryOperationsService` có là class riêng trong package
   `inventory.service` hay nằm trong 1 `InventoryTransactionRecorder` tách biệt khỏi
   `InventoryBalanceService` (đọc/cảnh báo tồn thấp)? — chưa chốt tên class/package cụ thể.
2. **DEP-001**: đã triển khai và kiểm thử cho Product Create V1 theo P-B. Triển khai môi trường và smoke test live được theo dõi riêng.
3. **Clock UTC (DEP-004/P06)**: `occurredAt` hiện chưa có nguồn chính thức — chưa thể code thật
   cho đến khi có `Clock` bean dùng chung.
4. **Current actor (DEP-002/P03)**: cách lấy `AppUser actor` từ principal chưa có cơ chế
   (`Authentication` chưa triển khai, P01-P04 đều PENDING).
5. **Error contract final (P05)**: `ProblemDetail` schema và mã lỗi ở mục 4-P-D chưa được duyệt
   chính thức, chỉ là đề xuất.
6. **Retry parameters (P09)**: số lần, backoff khi deadlock/lock timeout — chưa chốt, tạm thời
   `InventoryOperationsService` chỉ "fail fast", không tự retry.

→ **FND-008 có thể hoàn thiện phần thiết kế (tài liệu này) ngay**, nhưng **code thật của
`InventoryOperationsServiceImpl` cần chờ ít nhất mục 2–4 ở trên được chốt**, vì actor/time là
tham số bắt buộc, không thể mock tạm trong code production.

## 6. Việc có thể làm NGAY (không chờ PENDING ở mục 5)

Vì đây là phần nằm hoàn toàn trong module `inventory`, không phụ thuộc security/clock thật:

1. Viết **Repository interface** cần thiết:
   - `InventoryRepository`: thêm method `findByProductIdForUpdate(Long productId)` dùng
     `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
   - `InventoryTransactionRepository`: thêm `existsByPurchaseItemId`, `existsBySalesOrderItemId`,
     `existsBySalesReturnItemId`, `existsByAdjustmentItemId`,
     `existsByReversalOfTransactionId`.
2. Viết **interface** `InventoryOperationsService` (5 method ở mục 1) — compile được ngay vì chỉ
   phụ thuộc Entity đã có.
3. Viết **unit test** cho các rule row-local không phụ thuộc actor/clock thật (dùng actor/time
   giả lập trong test): check-all-first rollback, chặn SKU bị stocktake khóa, chặn adjustment
   delta làm âm tồn, chặn gọi `applyReturnIn` với `restockQuantity = 0`.
4. **Không viết** `*ServiceImpl` thật với logic gọi Spring Security principal / Clock bean cho đến
   khi mục 5.3, 5.4 được chốt — nếu cần, dùng interface `CurrentActorProvider` / `BusinessClock`
   dạng placeholder (đã thấy tên gợi ý trong backend-conventions.md) để không block code review,
   nhưng đánh dấu rõ TODO chờ DEP-002/004.

## 7. Checklist trước khi đóng FND-008

- [ ] Gửi tài liệu này cho TV1 review phần P-B (DEP-001) và mục 5.3–5.4 (DEP-002/004).
- [ ] Repository interface (mục 6.1) được review và merge.
- [ ] Interface `InventoryOperationsService` (mục 6.2) được review và merge.
- [ ] Unit test row-local (mục 6.3) pass.
- [ ] Cập nhật `implementation-tracker.md`: FND-008 TODO → IN_REVIEW kèm link PR/tài liệu.
- [ ] Sau khi P03/P04/P06 có quyết định chính thức → viết `InventoryOperationsServiceImpl` đầy đủ,
      chuyển FND-008 → DONE, mở khóa PUR-002/SAL-002 (DEP-005/006).
