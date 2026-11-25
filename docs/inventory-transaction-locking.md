# FND-009 → FND-011 — Inventory Transaction, Locking và Idempotency

> **Trạng thái:** DRAFT để nhóm xét duyệt. Soạn bởi TV2 (Nguyễn Phạm Gia Huy), tiếp nối
> [FND-008-inventory-operations-contract.md](FND-008-inventory-operations-contract.md) (đã gửi TV1 review).
> Nguồn: `database-design.md` §4.2, §4.5, §5, §7; `backend-conventions.md` §F, §G, §H;
> `authorization-permission-matrix.md` G3–G7 (xác nhận chéo, không mâu thuẫn với FND-008).
> Việc soạn tài liệu KHÔNG đồng nghĩa các mục PROPOSED/PENDING đã được duyệt.

Ba FND này giải quyết 3 câu hỏi khác nhau của cùng `InventoryOperationsService`:

| ID | Câu hỏi | Output |
|---|---|---|
| FND-009 | **Khóa cái gì, theo thứ tự nào, bằng cơ chế gì?** | Repository method có `@Lock`, quy tắc lock order |
| FND-010 | **Ghi ledger như thế nào cho đúng bất biến?** | Logic thật trong `InventoryOperationsServiceImpl` |
| FND-011 | **Confirm lặp / 2 request đua nhau thì sao?** | Pattern status-check-under-lock + retry policy |

## FND-009 — Transaction & Locking

### 1. Transaction owner (nhắc lại từ FND-008 §B.2, giờ ghi chính thức)

`InventoryOperationsService.applyXxx()` là `@Transactional(propagation = Propagation.REQUIRED)`.
Nó **không bao giờ** được gọi đứng một mình — luôn được gọi từ bên trong transaction đã mở bởi
`PurchaseService.confirm()`, `SalesOrderService.confirm()`, `SalesReturnService.confirm()`,
`InventoryAdjustmentService.approve()`, `SalesOrderService.cancel()`. Khớp
backend-conventions.md §F: *"Không dùng REQUIRES_NEW cho movement hoặc bước bắt buộc phải
rollback cùng chứng từ."*

### 2. Thứ tự khóa (BASELINE — đã xác nhận chéo ở 2 nguồn độc lập)

```
1. Khóa header/chứng từ (purchase / sales_order / sales_return / stocktake / inventory_adjustment)
2. Khóa payment liên quan, nếu có — theo payment.id tăng dần
3. Khóa inventory — theo inventory.product_id tăng dần
```

Bước 1–2 thuộc trách nhiệm của **Service gọi** (Purchase/Sales/...), xảy ra **trước khi** gọi vào
`InventoryOperationsService`. FND-009 chỉ định nghĩa bước 3.

### 3. Cơ chế khóa `inventory` — 1 dòng

```java
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.product.id = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);
}
```

### 4. Cơ chế khóa `inventory` — nhiều dòng (Sales nhiều SKU) — ĐIỂM CẦN NHÓM DUYỆT

Hai phương án, **chưa chốt**, cần ghi quyết định trước khi code `applySaleOut`:

**Phương án A (đề xuất) — một câu lệnh, `ORDER BY` + `FOR UPDATE`:**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select i from Inventory i where i.product.id in :productIds order by i.product.id asc")
List<Inventory> findAllByProductIdInForUpdate(@Param("productIds") List<Long> productIds);
```
MySQL InnoDB cấp lock theo đúng thứ tự quét của câu lệnh khi có `ORDER BY` trên cột đã index
(`product_id` có UNIQUE index) — tức là lock được cấp tăng dần tự động, không cần code Java tự
lặp. Ưu điểm: 1 round-trip, đơn giản, đúng thứ tự không phụ thuộc Hibernate.

**Phương án B — nhiều câu lệnh, Java tự sort rồi lặp `findByProductIdForUpdate` tuần tự:**
Chắc chắn đúng thứ tự vì Java kiểm soát từng lệnh, nhưng N round-trip, chậm hơn với đơn nhiều dòng.

→ **PENDING**: chọn A hay B là quyết định kỹ thuật cần ghi vào conventions trước khi
`applySaleOut` được code thật (không tự chọn khi review).

### 5. Deadlock / lock timeout

Theo backend-conventions.md §H (BASELINE): *"deadlock/lock timeout rollback toàn transaction và
retry có giới hạn từ đầu."* → Trong `InventoryOperationsService`: **không tự bắt** `PessimisticLockException`/
`CannotAcquireLockException` để retry — để exception nổi lên tầng gọi (Controller/advice), vì
retry phải **mở transaction mới hoàn toàn** (đã rollback hết), không thể retry từ giữa
`InventoryOperationsService`. Số lần retry cụ thể, backoff — xem FND-011 §4 (vẫn PENDING, P09).

## FND-010 — Inventory Movement Ledger (logic thật trong `*ServiceImpl`)

### 1. Khung chung của mọi `applyXxx()`

```java
// Khung dùng chung cho applyPurchaseIn / applyReturnIn / applyAdjustment / applySaleCancel
// (applySaleOut khác ở bước 2 vì xử lý nhiều dòng — xem §3 dưới)
Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
        .orElseThrow(() -> new InventoryRowNotFoundException(productId)); // FND-008 P-C

assertNotBlockedByStocktakeOrAdjustment(productId); // §2 dưới

long newQuantity = inventory.getQuantityOnHand() + delta; // delta âm cho OUT
if (newQuantity < 0) {
    throw new InsufficientStockException(List.of(new ShortfallLine(productId, -delta, inventory.getQuantityOnHand())));
}

inventory.setQuantityOnHand(newQuantity); // managed entity — Hibernate tự tăng version khi flush

InventoryTransaction txn = new InventoryTransaction();
txn.setProduct(inventory.getProduct());
txn.setTransactionType(type);
txn.setQuantityDelta(delta);
txn.setQuantityAfter(newQuantity);     // snapshot tính TẠI CHỖ, không query lại DB
txn.setOccurredAt(occurredAt);
txn.setRecordedBy(actorRef);           // getReferenceById(actor.userId()), xem FND-008 §2 mục 2
txn.setPurchaseItem(/* đúng 1 trong 4 source, phần còn lại null */);
return inventoryTransactionRepository.save(txn);
```

**Lưu ý bắt buộc** (backend-conventions.md §G): **không** dùng bulk update
(`UPDATE inventory SET quantity_on_hand = ...`) hay native query để đổi `quantity_on_hand` —
phải đi qua managed Entity (`inventory.setQuantityOnHand(...)` rồi để Hibernate tự `UPDATE` khi
flush/commit) để `@Version` được Hibernate quản lý đúng.

### 2. Chặn SKU đang stocktake/adjustment mở — cách hiện thực (database-design.md §4.5)

```java
private void assertNotBlockedByStocktakeOrAdjustment(Long productId) {
    boolean stocktakeOpen = stocktakeItemRepository
            .existsByProductIdAndCountedAtIsNullAndStocktake_Status(productId, StocktakeStatus.IN_PROGRESS);
    boolean adjustmentOpen = inventoryAdjustmentItemRepository
            .existsByProductIdAndAdjustment_Status(productId, InventoryAdjustmentStatus.PENDING);
    if (stocktakeOpen || adjustmentOpen) {
        throw new SkuLockedByStocktakeException(productId);
    }
}
```
Gọi hàm này **sau khi** đã khóa `inventory` row (bước 3 của lock order) — đúng yêu cầu
*"service phải khóa cùng dòng inventory, kiểm tra item đếm/adjustment mở rồi mới
snapshot/ghi movement"* (database-design.md §4.5). Cần thêm 2 method `existsBy...` vào
`StocktakeItemRepository` và `InventoryAdjustmentItemRepository` (chưa có, phải tạo).

### 3. `applySaleOut` — khác biệt: check-all-first trên nhiều dòng

```java
List<Inventory> locked = inventoryRepository.findAllByProductIdInForUpdate(sortedProductIds); // FND-009 §4-A
Map<Long, Inventory> byProduct = toMap(locked);

List<ShortfallLine> shortfalls = new ArrayList<>();
for (SalesOrderItem item : items) {
    Inventory inv = byProduct.get(item.getProduct().getId());
    assertNotBlockedByStocktakeOrAdjustment(item.getProduct().getId());
    if (inv.getQuantityOnHand() < item.getQuantity()) {
        shortfalls.add(new ShortfallLine(item.getProduct().getId(), item.getQuantity(), inv.getQuantityOnHand()));
    }
}
if (!shortfalls.isEmpty()) {
    throw new InsufficientStockException(shortfalls); // KHÔNG update gì, KHÔNG insert gì — rollback tự nhiên vì chưa ghi
}
// Chỉ sau khi vòng lặp trên pass hết, mới thực sự trừ + insert cho từng dòng
List<InventoryTransaction> result = new ArrayList<>();
for (SalesOrderItem item : items) {
    Inventory inv = byProduct.get(item.getProduct().getId());
    inv.setQuantityOnHand(inv.getQuantityOnHand() - item.getQuantity());
    result.add(inventoryTransactionRepository.save(buildSaleOutTxn(inv, item, actorRef, occurredAt)));
}
return result;
```

### 4. Mapping loại transaction ↔ source FK (bất biến CHECK, không được vi phạm)

| `transactionType` | FK phải set | FK khác | `reversal_of_transaction_id` | `reversal_reason` |
|---|---|---|---|---|
| `PURCHASE_IN` | `purchase_item_id` | null | null | null |
| `SALE_OUT` | `sales_order_item_id` | null | null | null |
| `RETURN_IN` | `sales_return_item_id` | null | null | null |
| `ADJUSTMENT_IN`/`ADJUSTMENT_OUT` | `adjustment_item_id` | null | null | null |
| `SALE_CANCEL_IN` | — tất cả 4 FK null | | `reversalOfTransaction` id của `SALE_OUT` gốc | bắt buộc NOT NULL |

Nếu build sai tổ hợp này, insert sẽ fail ở `ck_inventory_transaction_source` (lớp bảo vệ cuối) —
nhưng **phải đúng ngay từ code**, không dựa vào CHECK để bắt lỗi logic.

## FND-011 — Concurrency & Idempotency

### 1. Idempotency — nơi check thuộc về Service gọi, không phải `InventoryOperationsService`

Theo đúng backend-conventions.md §H: *"kiểm tra trạng thái dưới khóa header trước mọi side
effect; chỉ trạng thái hợp lệ mới được xử lý."*

```java
// Trong PurchaseService.confirm(Long purchaseId) — KHÔNG phải trong InventoryOperationsService
Purchase purchase = purchaseRepository.findByIdForUpdate(purchaseId) // khóa header TRƯỚC
        .orElseThrow(() -> new NotFoundException(...));
if (purchase.getStatus() != PurchaseStatus.DRAFT) {
    throw new DocumentAlreadyConfirmedException(purchaseId); // 409 DOCUMENT_ALREADY_CONFIRMED
}
// ... validate supplier/product active, qty > 0 ...
for (PurchaseItem item : purchase.getItems()) {
    inventoryOperationsService.applyPurchaseIn(item, actor, occurredAt); // UNIQUE constraint là lớp cuối, không phải lớp chính
}
purchase.setStatus(PurchaseStatus.CONFIRMED);
purchase.setConfirmedBy(actorRef);
purchase.setConfirmedAt(occurredAt);
```

`InventoryOperationsService` **vẫn nên** có lớp bảo vệ thứ hai (không bắt buộc chặn request,
nhưng nên log) — bắt `DataIntegrityViolationException` khi insert trùng UNIQUE source, và nếu
bắt được thì **đọc lại** bản ghi `InventoryTransaction` cũ theo source id, trả về bản ghi đó thay
vì ném lỗi ra ngoài — để trường hợp race hiếm (2 request lọt qua check status gần như đồng thời)
vẫn trả kết quả đúng nghĩa thay vì 500.

### 2. Test case bắt buộc cho FND-011 (ghi vào Definition of Done)

| # | Kịch bản | Kỳ vọng |
|---|---|---|
| 1 | Confirm cùng 1 `purchase_id` 2 lần liên tiếp (tuần tự) | Lần 2 → `409 DOCUMENT_ALREADY_CONFIRMED`, không tạo thêm `InventoryTransaction` |
| 2 | 2 thread confirm cùng 1 `purchase_id` gần như đồng thời | Chỉ 1 thread thành công, thread kia hoặc chờ lock rồi thấy status đã đổi (409), hoặc rơi vào UNIQUE (lớp 2) — không bao giờ có 2 `PURCHASE_IN` |
| 3 | 2 `SalesOrder` tranh cùng 1 SKU, tổng cầu > tồn | Đúng 1 đơn confirm được, đơn kia nhận `409 INSUFFICIENT_STOCK` |
| 4 | 1 `SalesOrder` 3 dòng, dòng thứ 3 thiếu tồn | Rollback toàn bộ — dòng 1, 2 cũng không bị trừ tồn |
| 5 | Hủy `SalesOrder` đã `CONFIRMED` có 2 dòng | 2 `SALE_CANCEL_IN` được tạo, mỗi cái reference đúng 1 `SALE_OUT` gốc; tổng tồn khôi phục đúng |
| 6 | Gọi `applyReturnIn` với `restockQuantity = 0` | Không có `InventoryTransaction` nào được tạo (không insert `quantity_delta = 0`) |
| 7 | SKU đang có `stocktake_item` IN_PROGRESS chưa đếm → gọi `applyPurchaseIn` cho SKU đó | `409 SKU_LOCKED_BY_STOCKTAKE`, không có transaction nào được tạo |
| 8 | 2 `SalesOrder` chạm 2 SKU theo thứ tự ngược nhau (đơn X: SKU 5→3, đơn Y: SKU 3→5) | Không deadlock — nhờ cả hai đều khóa theo `product_id` tăng dần (3 trước 5), bất kể thứ tự dòng trong đơn |

### 3. Retry policy — vẫn PENDING (P09), ghi rõ để không tự quyết khi code

Chưa chốt: số lần retry, backoff, exception nào được retry (chỉ deadlock/lock-timeout, **không**
retry `InsufficientStockException`/`SkuLockedByStocktakeException`/lỗi quyền/lỗi trạng thái),
retry ở tầng nào (Controller advice? AOP riêng?). **Việc có thể làm ngay:** code
`InventoryOperationsService` **fail-fast**, không tự retry gì cả — để chỗ trống (TODO comment
trỏ P09) cho tầng điều phối retry thêm vào sau khi nhóm chốt.

## Checklist trước khi đóng FND-009–011

- [ ] Nhóm chốt phương án A/B ở FND-009 §4 (lock nhiều dòng).
- [ ] `InventoryRepository`, `StocktakeItemRepository`, `InventoryAdjustmentItemRepository` có đủ method ở trên.
- [ ] `InventoryOperationsServiceImpl` implement đủ 5 method, pass 8 test case ở FND-011 §2 (dùng actor/clock giả lập trong test nếu DEP-002/004 chưa merge main).
- [ ] Cập nhật `implementation-tracker.md`: FND-009/010/011 TODO → IN_REVIEW kèm link PR.
- [ ] Mở khóa DEP-005 (PUR-002) và DEP-006 (SAL-002) sau khi merge.
