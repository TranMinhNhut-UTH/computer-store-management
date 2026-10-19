# Software Requirements

## 1. Mục đích

Tài liệu này mô tả các yêu cầu phần mềm cho hệ thống **Computer Store Management** dựa trên phạm vi V1 đã chốt trong `project-scope.md`.

Các yêu cầu được chia thành:

- Functional Requirements (FR): hệ thống phải làm được gì.
- Non-functional Requirements (NFR): hệ thống phải đáp ứng các yêu cầu chất lượng và kỹ thuật nào.

Các business rule chi tiết sẽ tiếp tục được tách sang `business-rules.md` ở bước tiếp theo.

## 2. Actors

| Actor | Trách nhiệm chính |
|---|---|
| Admin | Quản lý tài khoản nhân viên và quyền truy cập |
| Manager | Quản lý dữ liệu kinh doanh, duyệt điều chỉnh kho, xem báo cáo |
| Sales Staff | Quản lý khách hàng, bán hàng, thanh toán, trả hàng |
| Warehouse Staff | Nhập hàng, kiểm kê, theo dõi và đề xuất điều chỉnh tồn kho |

## 3. Functional Requirements

### 3.1. Authentication & Authorization

**FR-AUTH-01**  
Người dùng phải đăng nhập trước khi sử dụng các chức năng nội bộ của hệ thống.

**FR-AUTH-02**  
Hệ thống phải từ chối đăng nhập khi tài khoản không tồn tại, thông tin xác thực không hợp lệ hoặc tài khoản đã bị khóa.

**FR-AUTH-03**  
Sau khi đăng nhập thành công, hệ thống phải xác định được người dùng và vai trò/quyền của họ.

**FR-AUTH-04**  
Mỗi API được bảo vệ phải kiểm tra authentication trước khi xử lý yêu cầu.

**FR-AUTH-05**  
Hệ thống phải từ chối thao tác nếu người dùng đã đăng nhập nhưng không có quyền thực hiện chức năng đó.

**FR-AUTH-06**  
Người dùng phải có khả năng đăng xuất khỏi hệ thống.

---

### 3.2. User Management

**FR-USR-01**  
Admin có thể xem danh sách tài khoản nhân viên.

**FR-USR-02**  
Admin có thể tạo tài khoản nhân viên và gán vai trò phù hợp.

**FR-USR-03**  
Admin có thể cập nhật các thông tin tài khoản được phép chỉnh sửa.

**FR-USR-04**  
Admin có thể khóa hoặc mở khóa tài khoản nhân viên.

**FR-USR-05**  
Tài khoản bị khóa không được phép đăng nhập.

**FR-USR-06**  
Hệ thống không được xóa vật lý tài khoản nếu việc xóa làm mất liên kết với các giao dịch lịch sử; thay vào đó phải ưu tiên trạng thái khóa/inactive.

---

### 3.3. Category Management

**FR-CAT-01**  
Manager có thể xem danh sách danh mục sản phẩm.

**FR-CAT-02**  
Manager có thể tạo danh mục mới.

**FR-CAT-03**  
Manager có thể cập nhật thông tin danh mục.

**FR-CAT-04**  
Manager có thể ngừng sử dụng một danh mục khi phù hợp.

**FR-CAT-05**  
Hệ thống phải ngăn việc xóa vật lý danh mục nếu danh mục đang được tham chiếu bởi sản phẩm hoặc dữ liệu lịch sử cần bảo toàn.

---

### 3.4. Product Management

**FR-PRD-01**  
Người dùng có quyền có thể xem danh sách và thông tin chi tiết sản phẩm.

**FR-PRD-02**  
Manager có thể tạo sản phẩm mới và gán sản phẩm vào danh mục.

**FR-PRD-03**  
Manager có thể cập nhật thông tin sản phẩm.

**FR-PRD-04**  
Chỉ Manager hoặc vai trò có quyền tương đương mới được thay đổi giá bán sản phẩm.

**FR-PRD-05**  
Manager có thể chuyển sản phẩm sang trạng thái inactive/ngừng kinh doanh.

**FR-PRD-06**  
Sản phẩm đã phát sinh giao dịch không được xóa vật lý theo cách làm mất lịch sử tham chiếu.

**FR-PRD-07**  
Mỗi sản phẩm có thể khai báo `MinStockLevel` để phục vụ cảnh báo tồn thấp.

**FR-PRD-08**  
Hệ thống phải hỗ trợ tìm kiếm và phân trang danh sách sản phẩm.

---

### 3.5. Supplier Management

**FR-SUP-01**  
Manager có thể xem danh sách và chi tiết nhà cung cấp.

**FR-SUP-02**  
Manager có thể tạo nhà cung cấp mới.

**FR-SUP-03**  
Manager có thể cập nhật thông tin nhà cung cấp.

**FR-SUP-04**  
Manager có thể chuyển nhà cung cấp sang trạng thái inactive khi không còn giao dịch mới.

**FR-SUP-05**  
Nhà cung cấp đã được sử dụng trong phiếu nhập không được xóa vật lý nếu việc xóa làm mất lịch sử giao dịch.

---

### 3.6. Customer Management

**FR-CUS-01**  
Sales Staff có thể xem và tìm kiếm khách hàng.

**FR-CUS-02**  
Sales Staff có thể tạo hồ sơ khách hàng khi cần lưu thông tin phục vụ giao dịch.

**FR-CUS-03**  
Sales Staff có thể cập nhật thông tin khách hàng.

**FR-CUS-04**  
Hệ thống phải cho phép nghiệp vụ bán hàng hoạt động mà không yêu cầu khách hàng có tài khoản đăng nhập.

**FR-CUS-05**  
Khách hàng đã phát sinh giao dịch không được xóa vật lý nếu việc xóa làm mất lịch sử đơn hàng.

---

### 3.7. Purchase / Stock In

**FR-PUR-01**  
Warehouse Staff có thể tạo phiếu nhập ở trạng thái `DRAFT`.

**FR-PUR-02**  
Phiếu nhập phải xác định nhà cung cấp và chứa ít nhất một dòng sản phẩm hợp lệ trước khi được xác nhận.

**FR-PUR-03**  
Warehouse Staff có thể thêm, sửa hoặc xóa dòng hàng khi phiếu nhập còn ở trạng thái `DRAFT`.

**FR-PUR-04**  
Warehouse Staff có thể xác nhận phiếu nhập hợp lệ.

**FR-PUR-05**  
Khi phiếu nhập chuyển từ `DRAFT` sang `CONFIRMED`, hệ thống phải tăng tồn kho theo đúng số lượng của từng dòng hàng.

**FR-PUR-06**  
Việc xác nhận phiếu nhập và tăng tồn kho phải được thực hiện như một đơn vị giao dịch nhất quán; không được xảy ra trường hợp phiếu đã xác nhận nhưng tồn kho chỉ cập nhật một phần.

**FR-PUR-07**  
Một phiếu nhập đã `CONFIRMED` không được xác nhận lần thứ hai.

**FR-PUR-08**  
Phiếu nhập đã `CONFIRMED` không được sửa trực tiếp các dữ liệu làm thay đổi số lượng tồn kho đã ghi nhận.

**FR-PUR-09**  
Hệ thống phải lưu lịch sử giao dịch kho phát sinh từ việc xác nhận phiếu nhập.

**FR-PUR-10**  
Người dùng có quyền có thể xem danh sách và chi tiết phiếu nhập theo trạng thái và thời gian.

---

### 3.8. Inventory Management

**FR-INV-01**  
Người dùng có quyền có thể xem tồn kho hiện tại của từng sản phẩm.

**FR-INV-02**  
Hệ thống phải lưu lịch sử biến động tồn kho cho các nghiệp vụ làm tăng hoặc giảm tồn.

**FR-INV-03**  
Mỗi giao dịch tồn kho phải xác định được loại biến động, sản phẩm, số lượng thay đổi, thời điểm và nguồn nghiệp vụ liên quan khi có.

**FR-INV-04**  
Warehouse Staff không được sửa trực tiếp số lượng tồn kho hiện tại bằng một thao tác CRUD thông thường.

**FR-INV-05**  
Warehouse Staff có thể thực hiện kiểm kê và nhập số lượng thực tế đếm được.

**FR-INV-06**  
Khi phát hiện chênh lệch, Warehouse Staff có thể tạo đề xuất điều chỉnh tồn kho.

**FR-INV-07**  
Đề xuất điều chỉnh tồn kho phải có lý do.

**FR-INV-08**  
Chỉ Manager hoặc vai trò có quyền tương đương mới được duyệt đề xuất điều chỉnh.

**FR-INV-09**  
Tồn kho chỉ thay đổi theo đề xuất điều chỉnh sau khi đề xuất được duyệt.

**FR-INV-10**  
Sau khi điều chỉnh được duyệt, hệ thống phải tạo giao dịch tồn kho tương ứng để lưu lịch sử.

**FR-INV-11**  
Hệ thống phải xác định được các sản phẩm có tồn kho nhỏ hơn hoặc bằng `MinStockLevel` để phục vụ cảnh báo tồn thấp.

**FR-INV-12**  
Hệ thống phải hỗ trợ xem lịch sử giao dịch kho theo sản phẩm và khoảng thời gian.

---

### 3.9. Sales Order

**FR-ORD-01**  
Sales Staff có thể tạo đơn bán hàng ở trạng thái `DRAFT`.

**FR-ORD-02**  
Sales Staff có thể thêm, sửa hoặc xóa dòng sản phẩm khi đơn còn ở trạng thái `DRAFT`.

**FR-ORD-03**  
Hệ thống phải tính thành tiền của từng dòng và tổng tiền của đơn từ dữ liệu hợp lệ tại thời điểm lập/xác nhận đơn.

**FR-ORD-04**  
Sales Staff có thể xác nhận đơn `DRAFT` hợp lệ.

**FR-ORD-05**  
Trước khi xác nhận, hệ thống phải kiểm tra tồn kho của tất cả sản phẩm trong đơn.

**FR-ORD-06**  
Hệ thống không được cho phép xác nhận đơn nếu số lượng yêu cầu của bất kỳ sản phẩm nào lớn hơn tồn kho khả dụng.

**FR-ORD-07**  
Khi đơn chuyển từ `DRAFT` sang `CONFIRMED`, hệ thống phải giảm tồn kho theo số lượng của từng dòng hàng.

**FR-ORD-08**  
Việc xác nhận đơn và giảm tồn kho phải được thực hiện như một đơn vị giao dịch nhất quán.

**FR-ORD-09**  
Một đơn đã `CONFIRMED` không được xác nhận lần thứ hai.

**FR-ORD-10**  
Sales Staff có thể hủy đơn khi trạng thái và business rule cho phép.

**FR-ORD-11**  
Đơn ở trạng thái `COMPLETED` không được xóa vật lý.

**FR-ORD-12**  
Các thay đổi sau khi đơn đã tác động đến tồn kho phải đi qua nghiệp vụ được kiểm soát, không được sửa trực tiếp làm sai lịch sử kho.

**FR-ORD-13**  
Hệ thống phải lưu giao dịch tồn kho phát sinh khi xác nhận đơn bán hàng.

**FR-ORD-14**  
Người dùng có quyền có thể xem, tìm kiếm và lọc lịch sử đơn hàng theo các tiêu chí cơ bản như mã đơn, trạng thái, khách hàng và thời gian.

---

### 3.10. Payment

> Trong V1, trạng thái thanh toán được quản lý riêng với trạng thái nghiệp vụ của đơn hàng.

**FR-PAY-01**  
Sales Staff có thể ghi nhận một khoản thanh toán cho đơn hàng hợp lệ.

**FR-PAY-02**  
Mỗi khoản thanh toán phải lưu tối thiểu số tiền, phương thức ghi nhận, thời điểm và người thực hiện.

**FR-PAY-03**  
Hệ thống phải tính được tổng số tiền đã thanh toán và số tiền còn phải thanh toán của đơn.

**FR-PAY-04**  
Hệ thống không được cho phép tổng tiền thanh toán hợp lệ vượt quá số tiền cần thanh toán của đơn, trừ khi sau này có business rule riêng cho tiền thừa/hoàn tiền.

**FR-PAY-05**  
Hệ thống phải xác định được trạng thái thanh toán cơ bản của đơn như `UNPAID`, `PARTIALLY_PAID` hoặc `PAID` dựa trên các khoản thanh toán đã ghi nhận.

**FR-PAY-06**  
V1 chỉ ghi nhận thanh toán nội bộ, không gửi yêu cầu sang payment gateway bên ngoài.

---

### 3.11. Return

**FR-RET-01**  
Sales Staff có thể tạo yêu cầu trả hàng dựa trên một đơn bán hàng đã tồn tại và đủ điều kiện trả theo business rule.

**FR-RET-02**  
Phiếu trả hàng phải xác định các sản phẩm và số lượng được trả.

**FR-RET-03**  
Hệ thống không được cho phép tổng số lượng trả của một sản phẩm vượt quá số lượng đã mua sau khi trừ các lần trả trước đó.

**FR-RET-04**  
Khi một phiếu trả hàng hợp lệ được xác nhận, hệ thống phải cập nhật tồn kho theo quy tắc V1 và tạo giao dịch kho tương ứng.

**FR-RET-05**  
Hệ thống phải giữ liên kết giữa phiếu trả hàng và đơn bán hàng gốc.

**FR-RET-06**  
Việc hoàn tiền tự động qua cổng thanh toán nằm ngoài phạm vi V1; nếu có hoàn tiền, V1 chỉ ghi nhận nghiệp vụ nội bộ theo business rule được chốt sau.

---

### 3.12. Reports

**FR-RPT-01**  
Manager có thể xem báo cáo doanh thu cơ bản theo khoảng thời gian.

**FR-RPT-02**  
Manager có thể xem báo cáo hoặc danh sách sản phẩm bán ra theo khoảng thời gian ở mức phù hợp với V1.

**FR-RPT-03**  
Manager có thể xem báo cáo tồn kho hiện tại.

**FR-RPT-04**  
Manager có thể xem danh sách sản phẩm sắp hết hàng dựa trên `MinStockLevel`.

**FR-RPT-05**  
Các báo cáo phải dựa trên dữ liệu giao dịch đã được ghi nhận trong hệ thống, không yêu cầu tích hợp hệ thống kế toán bên ngoài trong V1.

## 4. Non-functional Requirements

### 4.1. Security

**NFR-SEC-01**  
Mật khẩu không được lưu dưới dạng plaintext; hệ thống phải sử dụng cơ chế hash mật khẩu phù hợp.

**NFR-SEC-02**  
Các API nội bộ cần bảo vệ phải yêu cầu authentication.

**NFR-SEC-03**  
Khi người dùng đã xác thực nhưng không có quyền, API phải trả về phản hồi tương ứng với `403 Forbidden`.

**NFR-SEC-04**  
Hệ thống không được tin cậy dữ liệu quyền hạn do frontend tự khai báo; authorization phải được kiểm tra ở backend.

**NFR-SEC-05**  
Dữ liệu đầu vào từ client phải được validate trước khi thực hiện nghiệp vụ hoặc lưu xuống database.

---

### 4.2. Data Integrity & Transactions

**NFR-DAT-01**  
Các nghiệp vụ vừa thay đổi trạng thái chứng từ vừa cập nhật tồn kho phải sử dụng transaction hoặc cơ chế tương đương để đảm bảo tính nhất quán.

**NFR-DAT-02**  
Hệ thống phải ngăn việc confirm cùng một phiếu nhập hoặc đơn bán nhiều lần làm tăng/giảm tồn lặp.

**NFR-DAT-03**  
Dữ liệu lịch sử giao dịch quan trọng không được mất do xóa vật lý tùy tiện.

**NFR-DAT-04**  
Các ràng buộc quan trọng nên được bảo vệ ở cả application layer và database khi phù hợp, ví dụ foreign key, unique constraint và check constraint.

**NFR-DAT-05**  
Hệ thống phải có chiến lược xử lý concurrent update đối với các nghiệp vụ có thể làm thay đổi tồn kho để giảm nguy cơ overselling hoặc cập nhật sai số lượng.

---

### 4.3. Performance

**NFR-PER-01**  
Các API danh sách có khả năng tăng dữ liệu lớn phải hỗ trợ pagination.

**NFR-PER-02**  
Các trường thường dùng để tìm kiếm, lọc, join hoặc kiểm tra uniqueness nên được đánh index phù hợp trong database sau khi thiết kế ERD.

**NFR-PER-03**  
Các API danh sách không nên trả về toàn bộ dữ liệu không giới hạn khi số lượng bản ghi có thể tăng theo thời gian.

---

### 4.4. Maintainability

**NFR-MNT-01**  
Backend phải tách biệt hợp lý giữa API/controller, business/service logic và data access layer để tránh đặt toàn bộ nghiệp vụ trong controller.

**NFR-MNT-02**  
API sử dụng DTO/request-response model phù hợp thay vì mặc định expose trực tiếp toàn bộ entity database.

**NFR-MNT-03**  
Các mã trạng thái, loại giao dịch và quy tắc quan trọng phải được biểu diễn nhất quán, tránh sử dụng magic string rải rác.

**NFR-MNT-04**  
Các thay đổi cấu trúc database phải được quản lý bằng công cụ database migration phù hợp (ví dụ Flyway hoặc tương đương).

---

### 4.5. Auditability & Error Handling

**NFR-AUD-01**  
Các giao dịch nghiệp vụ quan trọng cần lưu thông tin thời điểm tạo/xác nhận và người thực hiện khi phù hợp.

**NFR-AUD-02**  
Inventory transaction phải cho phép truy vết biến động tồn kho về nguồn phát sinh như purchase, sales order, return hoặc adjustment khi có.

**NFR-ERR-01**  
API phải trả về lỗi có cấu trúc và status code phù hợp cho các trường hợp validation error, unauthorized, forbidden, not found và business rule violation.

**NFR-ERR-02**  
Lỗi nội bộ không được trả stack trace hoặc thông tin nhạy cảm trực tiếp cho client trong môi trường production.

## 5. Ma trận quyền mức cao

Ký hiệu:

- `✓`: được phép trong phạm vi V1.
- `R`: chủ yếu chỉ đọc/xem.
- `—`: không thuộc trách nhiệm mặc định.

| Chức năng | Admin | Manager | Sales Staff | Warehouse Staff |
|---|:---:|:---:|:---:|:---:|
| Quản lý tài khoản nhân viên | ✓ | — | — | — |
| Quản lý sản phẩm/danh mục | — | ✓ | R | R |
| Thay đổi giá bán | — | ✓ | — | — |
| Quản lý nhà cung cấp | — | ✓ | — | R |
| Quản lý khách hàng | — | R | ✓ | — |
| Tạo/sửa phiếu nhập DRAFT | — | R | — | ✓ |
| Xác nhận phiếu nhập | — | R | — | ✓ |
| Xem tồn kho | R | ✓ | R | ✓ |
| Kiểm kê | — | R | — | ✓ |
| Tạo đề xuất điều chỉnh | — | R | — | ✓ |
| Duyệt điều chỉnh tồn kho | — | ✓ | — | — |
| Tạo/xác nhận đơn bán | — | R | ✓ | — |
| Ghi nhận thanh toán | — | R | ✓ | — |
| Xử lý trả hàng | — | R | ✓ | R |
| Xem báo cáo | — | ✓ | — | — |

> Ma trận này là quyền mức cao phục vụ phân tích. Quyền API chi tiết sẽ được chốt ở bước thiết kế Use Case/API.

## 6. Các quyết định phân tích quan trọng

### 6.1. Tách Order Status và Payment Status

Không sử dụng một chuỗi trạng thái kiểu:

```text
DRAFT → CONFIRMED → PAID → COMPLETED
```

vì `PAID` mô tả trạng thái thanh toán, còn `CONFIRMED/COMPLETED` mô tả vòng đời nghiệp vụ của đơn.

V1 nên quản lý hai khái niệm riêng:

```text
OrderStatus:
DRAFT | CONFIRMED | COMPLETED | CANCELLED

PaymentStatus:
UNPAID | PARTIALLY_PAID | PAID
```

Cách này tránh làm state machine của đơn hàng bị phụ thuộc cứng vào cách khách thanh toán.

### 6.2. Inventory là dữ liệu nghiệp vụ, không phải ô số để sửa tự do

`CurrentStock` có thể được lưu để truy vấn nhanh, nhưng mọi thay đổi phải đi qua nghiệp vụ hợp lệ và tạo `InventoryTransaction`.

Nguồn biến động tối thiểu trong V1:

```text
PURCHASE_IN
SALE_OUT
RETURN_IN
ADJUSTMENT_IN
ADJUSTMENT_OUT
```

### 6.3. Không tách Purchase Order ở V1

V1 dùng một phiếu nhập có `DRAFT` và `CONFIRMED`. Đây là chủ ý giới hạn scope, không phải thiếu sót thiết kế.

## 7. Acceptance Scenarios ban đầu

Các scenario này chưa thay thế test case đầy đủ, nhưng dùng để kiểm tra requirement có đủ rõ trước khi vẽ Use Case và ERD.

### Scenario 1 — Không cho bán vượt tồn

```text
Given: Product A có tồn kho = 5
And: Sales Staff tạo đơn bán 6 sản phẩm A
When: Sales Staff xác nhận đơn
Then: Hệ thống từ chối xác nhận
And: Tồn kho vẫn = 5
```

### Scenario 2 — Xác nhận đơn trừ kho đúng một lần

```text
Given: Product A có tồn kho = 5
And: Đơn DRAFT chứa 3 sản phẩm A
When: Đơn được xác nhận
Then: Đơn chuyển thành CONFIRMED
And: Tồn kho Product A = 2
And: Có inventory transaction SALE_OUT = 3
And: Không thể confirm lại đơn để trừ thêm lần nữa
```

### Scenario 3 — Xác nhận phiếu nhập tăng kho đúng một lần

```text
Given: Product A có tồn kho = 5
And: Phiếu nhập DRAFT chứa 10 sản phẩm A
When: Phiếu nhập được xác nhận
Then: Phiếu chuyển thành CONFIRMED
And: Tồn kho Product A = 15
And: Có inventory transaction PURCHASE_IN = 10
And: Không thể confirm lại phiếu để tăng thêm lần nữa
```

### Scenario 4 — Điều chỉnh tồn kho cần duyệt

```text
Given: Hệ thống ghi nhận tồn Product A = 10
And: Warehouse Staff kiểm kê thực tế = 8
When: Warehouse Staff tạo adjustment với reason hợp lệ
Then: Tồn kho chưa thay đổi ngay
When: Manager duyệt adjustment
Then: Tồn kho Product A = 8
And: Có inventory transaction ADJUSTMENT_OUT = 2
```

### Scenario 5 — Kiểm tra quyền

```text
Given: Sales Staff đã đăng nhập
When: Gọi API thay đổi giá bán sản phẩm
Then: Hệ thống trả 403 Forbidden
And: Giá bán không thay đổi
```

## 8. Nội dung cần chốt ở bước tiếp theo

`requirements.md` đã đủ để chuyển sang Business Rules và Use Case, nhưng các chi tiết sau nên được khóa ở bước tiếp theo trước khi thiết kế database hoàn chỉnh:

- Điều kiện chính xác để chuyển đơn từ `CONFIRMED` sang `COMPLETED`.
- Đơn `CONFIRMED` có được `CANCELLED` hay phải dùng reversal/return.
- Chính sách trả hàng: thời hạn trả, sản phẩm nào được trả, hàng trả có luôn nhập lại kho hay không.
- Phương thức thanh toán nào được hỗ trợ trong V1.
- Manager chỉ xem phiếu nhập hay có quyền xác nhận thay Warehouse Staff.
- Quy tắc giá nhập, giá bán và có/không hỗ trợ giảm giá trong V1.
- Các báo cáo V1 cần chính xác đến mức nào: doanh thu, lợi nhuận, top sản phẩm, nhập-xuất-tồn.

Các điểm này không làm thay đổi scope tổng thể, nhưng sẽ ảnh hưởng trực tiếp đến `business-rules.md`, Use Case, ERD và API contract.
