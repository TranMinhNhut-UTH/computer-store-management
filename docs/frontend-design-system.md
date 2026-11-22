# CSM Frontend Design System V1

Giai đoạn A — UI Foundation, ngày 2026-11-18; bổ sung Phase B — App Shell/shared components ngày 2026-11-20. Dùng chung cho hai thành viên; không đổi ownership/module V1. Phase B còn local, chưa review/merge.

## Nguyên tắc

Modern Admin Dashboard, primary Blue, nội dung tiếng Việt; ưu tiên đọc nhanh, trạng thái rõ ràng, responsive và bàn phím. Phase A cung cấp tokens/theme và Login; Phase B thêm App Shell chỉ sau xác thực. Chưa có Dashboard hoặc trang nghiệp vụ; không thêm Warranty vào V1.

## Tokens và màu sắc

Nguồn chuẩn: `frontend/src/styles/tokens.css` (kích thước), `themes.css` (semantic colors), `global.css` (reset/accessibility). Component dùng `var(--csm-...)`, không tự chép hex hoặc phụ thuộc trực tiếp light/dark.

| Token `--csm-…` | Light | Dark | Cách dùng |
|---|---|---|---|
| bg | #F8FAFC | #0B1220 | Nền ứng dụng |
| surface | #FFFFFF | #151F30 | Bề mặt nội dung |
| sidebar | #FFFFFF | #101827 | Nền sidebar/drawer |
| text | #0F172A | #F1F5F9 | Nội dung chính |
| muted | #64748B | #94A3B8 | Nội dung phụ/placeholder trên nền bg/surface/input |
| border | #E2E8F0 | #334155 | Phân cách/trang trí, không dùng làm dấu nhận biết input duy nhất |
| primary | #2563EB | #60A5FA | Hành động chính |
| primary-soft | #DBEAFE | #1E3A5F | Nền nhấn, dùng text làm foreground |
| primary-hover | #1D4ED8 | #93C5FD | Hover hành động chính |
| on-primary | #FFFFFF | #0B1220 | Chữ/icon trên primary và primary-hover |
| success / success-soft | #15803D / #F0FDF4 | #4ADE80 / #123224 | Thông báo thành công |
| error / error-soft | #B91C1C / #FEF2F2 | #FCA5A5 / #3B1D29 | Thông báo lỗi |
| warning / warning-soft | #92400E / #FFFBEB | #FCD34D / #352C18 | Cảnh báo |
| focus | #2563EB | #93C5FD | Outline focus-visible |
| disabled / disabled-bg | #475569 / #E2E8F0 | #94A3B8 / #243247 | Control disabled, không dùng opacity để làm mờ text |
| hover | #EFF6FF | #1E293B | Hover control phụ |
| input-bg / input-border | #FFFFFF / #64748B | #101827 / #94A3B8 | Input dễ phân biệt với nền |
| card-bg | #FFFFFF | #151F30 | Card/dialog |
| table-bg / table-hover | #FFFFFF / #EFF6FF | #151F30 / #1E293B | Dự trữ cho table, không tạo table trong A |
| overlay | rgb(15 23 42 / 48%) | rgb(0 0 0 / 64%) | Dự trữ cho overlay/dialog |

Không mặc định mọi tổ hợp token đều đạt tương phản. Ví dụ muted dùng trên bg/surface, không mặc định trên primary-soft; ở Login brand panel dùng text. Token primary của dark không dùng chữ trắng: dùng on-primary. Borders trang trí không thay cho input-border/focus.

## Typography, spacing, radius

- Font system sans hỗ trợ tiếng Việt, fallback Segoe UI/sans-serif, không tải font ngoài.
- Font tokens xs/sm/md/lg/title lần lượt 12/14/16/20/32px ở root 16px; dùng rem, không khóa khả năng zoom. Line-height nội dung 1.6–1.8; heading 1.1–1.3.
- Base spacing 4px: space-1/2/3/4/5/6/8/10/12/16/20 = 4/8/12/16/20/24/32/40/48/64/80px. Dùng token cho padding/margin/gap; kích thước layout được phép theo nội dung.
- radius-control 8px; radius-card 12px; radius-round 50%. control-height 52px, vùng thao tác tối thiểu 44px.

## Theme usage

`index.html` có initializer nhỏ trong head: chỉ đọc `csm-theme`, áp `data-theme` và `color-scheme` trước React để hạn chế nháy sai theme. Quy tắc normalize phải đồng nhất với `src/theme/theme.js`; test kiểm tra cả hai.

`ThemeProvider` bọc root App một lần. Mặc định light, không tự chọn theo OS. Chỉ nhận/lưu light hoặc dark; giá trị lạ fallback light và được chuẩn hóa khi provider lưu. Storage bị chặn thì không crash; chuyển theme vẫn hoạt động trong trang hiện tại nhưng không đảm bảo persist. Không reload trang, không tác động auth/session/CSRF.

```jsx
import { useTheme } from './theme/useTheme.js'

function Example() {
  const { theme, setTheme, toggleTheme } = useTheme()
  return <button type="button" onClick={toggleTheme} aria-pressed={theme === 'dark'}>
    Chế độ tối
  </button>
}
```

Hook cần nằm trong ThemeProvider. ThemeToggle dùng chung đã có accessible name và aria-pressed. Không tạo provider riêng theo trang. Chỉ preference giao diện dùng localStorage; tuyệt đối không đưa password, CSRF token, cookie/session ID hoặc auth state vào đây. Không đọc/xóa các key không thuộc theme. Đồng bộ tức thời giữa nhiều tab chưa triển khai; tab mới đọc preference đã lưu.

## Component và naming conventions

- CSS variables: `--csm-<semantic-purpose>`; component React PascalCase; hooks `use…`; files JS/JSX.
- Component mới ưu tiên class có phạm vi/tên chức năng rõ, không tạo selector theme riêng bên trong từng component. Login giữ các class hiện có để tránh di chuyển code không cần thiết.
- Cấu trúc button/input/card dùng control/card tokens; icon nhận currentColor; không hardcode màu vào SVG. Không tạo component/thư mục trống cho tính năng tương lai.
- Error có text, không chỉ màu; giữ role alert/status, labels, autocomplete, show/hide password và state loading/disabled. ThemeToggle luôn độc lập với busy của auth.
- Auth client và endpoint không thay đổi trong giai đoạn này. Không giả lập authentication, không thêm router hoặc UI framework.

## Responsive và accessibility

Login hai cột ở viewport trên 800px, một cột ở 800px trở xuống; card fluid có max-width, nội dung không ép chiều cao cố định. Thử ở 320/390/768/1440px và zoom 200% trong browser khi khả dụng. Quy tắc layout tương lai tùy use case, không lấy breakpoint của Login làm yêu cầu cho mọi màn hình.

Yêu cầu chữ thường >=4.5:1; focus/biên input thiết yếu >=3:1 với nền liền kề. Outline focus-visible 3px, offset 3px; không bỏ outline mà không có thay thế. Native button dùng Enter/Space; không dùng div giả button. Disabled dùng native disabled; hỗ trợ prefers-reduced-motion; không ẩn thông tin chỉ vì đổi theme. Semantic tokens được kiểm tra tương phản theo cặp đã công bố; vẫn cần kiểm tra trực quan, bàn phím và zoom trên từng màn hình.

## Kiểm tra và giới hạn

Từ frontend:

```powershell
node --test src/theme/theme.test.js src/api/auth.test.js
npm run build
```

Tests theme kiểm tra default, toggle hai chiều, persist/invalid/storage failure, root attributes, initializer và tương phản màu; tests auth hiện có chạy nguyên trạng bằng mock fetch, không gửi POST thật. Khi browser không khả dụng, có thể kiểm tra React SSR nhưng không coi đó là browser QA. Không ghi DONE khi chưa review/merge. Chưa có tracker ID riêng cho Design System, giữ tracker hiện có.

Không sửa Backend/DB/Flyway, auth endpoint, ownership TV2. Phase A không có App Shell; Phase B chỉ có shell/coming-soon, chưa tạo màn hình nghiệp vụ. Không tự mở rộng sang Phase C.

## Phase B: App Shell và navigation

`App.jsx` giữ auth state/CSRF flow hiện có. Chỉ khi GET `/me` đã xác nhận user mới render `AppLayout`; không có đường dẫn/fragment, browser storage hoặc flag development để bypass Login. `/me` chỉ trả userId/username: không tự tạo role hoặc client permission mapping. Không có fake user/session trong source runtime.

`app/AppLayout.jsx` gồm Sidebar cố định, Topbar và MainContent. Topbar hiển thị username từ server, theme toggle và logout hiện có; không avatar/notification/tenant/role giả. Nav metadata nằm tại `app/navigation.js`; chọn mục cập nhật React state trong shell, không có router/URL routing. Tất cả mục đều là coming-soon, không gọi API nghiệp vụ, không có KPI hoặc dữ liệu mẫu. Có nhóm danh mục, giao dịch/kho và quản lý; payment/refund/return/adjustment nằm trong V1. Nhãn navigation-only không thay authorization của backend.

Responsive navigation: desktop >=1024px mặc định sidebar 256px (icon + label + nhóm); hamburger toggle 72px chỉ icon rồi mở rộng lại, MainContent tự điều chỉnh khoảng trống. Tablet 768–1023px mặc định sidebar 72px; người dùng có thể mở rộng/thu gọn bằng hamburger nếu đủ không gian. Trạng thái mở rộng của desktop và tablet độc lập trong bộ nhớ, không ghi storage. Desktop/tablet không render dialog, hamburger không mở overlay. Active navigation được giữ khi toggle/resize.

Mobile <768px không render sidebar trong layout; hamburger mở drawer native `<dialog>` có backdrop semantic overlay. Chỉ render menu trong drawer khi mở: không có hai bản navigation trùng nhau trong DOM. Nút mở có accessible name, aria-expanded/aria-controls; chỉ trên mobile có aria-haspopup=dialog. Khi mở, focus vào nút đóng và khóa cuộn body; native modal giữ focus trong dialog/inert phần ngoài. Đóng bằng nút, overlay, Escape, chọn menu hoặc resize sang tablet/desktop. Đóng bình thường trả focus về opener; chọn menu hoặc resize ra khỏi mobile chuyển focus tới MainContent và trả lại overflow trước đó. Quay về mobile không tự mở lại drawer.

Menu dùng SVG stroke/currentColor thống nhất, không thêm icon library. Rail thu gọn có aria-label và native title cho từng icon; nhóm có accessible name. Không lặp “Chưa triển khai” dưới từng menu: trạng thái chỉ nằm trong vùng nội dung. Không thay đổi danh sách module, API hoặc quyền. Giữ focus-visible, skip-link, main landmark và PageHeader h1. Ở màn hình nhỏ Topbar wrap để tránh tràn ngang, username dài được ngắt dòng.

## Shared components

| Component | Props chính | Quy ước |
|---|---|---|
| Button | variant primary/secondary/ghost, loading, disabled, type, children | Mặc định type button; loading vô hiệu click và có aria-busy; ref React 19 được chuyển xuống button |
| TextInput | label, id, error, hint và input props | useId khi thiếu id; nối label/help/error bằng aria-describedby; error có aria-invalid |
| Card | as, className, children, HTML props | Surface/card/border/radius theo tokens; đặt aria-label khi cần tên section |
| StatusBadge | variant neutral/success/warning/error, children | Luôn có chữ mô tả trạng thái; không chỉ phân biệt bằng màu |
| PageHeader | title, description, actions | Page title h1 và actions area; dùng một PageHeader cho content hiện tại |
| EmptyState | title, description, action | Không bịa dữ liệu; coming-soon giải thích chưa có chức năng |
| LoadingState | label | role status và spinner reduced-motion |

Các component nằm trong `components/ui/`, style tại `components/ui/ui.css`; shell tại `app/shell.css`. Tái sử dụng tokens của Phase A, không tạo palette/module CSS theme riêng. Không xây DataTable/Modal framework ở giai đoạn này. `PageHeader` hiện dùng id `page-title`, chỉ dùng một lần mỗi trang.

Hai thành viên dùng cùng AppLayout/components khi triển khai module được giao. Chỉ thay coming-soon bằng page thật khi có task, API/permission contract và tests được duyệt; không dùng menu hiển thị để suy quyền, không đổi ownership. Không đưa fixtures hoặc mock fetch vào runtime.

Test toàn bộ frontend:

```powershell
node --test src/app/AppLayout.test.js src/api/auth.test.js src/theme/theme.test.js
npm run build
```

`jsdom` là devDependency duy nhất thêm trong Phase B, dùng React act/createRoot cho interaction/auth gate. Các auth response chỉ là stub trong tests, không gửi HTTP thật. Dialog được adapter trong jsdom để kiểm tra open/close/focus; native focus trap/backdrop vẫn cần browser thật.

Responsive navigation được kiểm tra 2026-11-20 bằng Chrome headless local, profile test riêng và intercept toàn bộ API (không gọi Backend/DB): widths 320/390/767/768/900/1023/1024/1440px, sidebar 256/72px, margin content, không tràn ngang/trùng navigation; native dialog/Tab/Escape/backdrop/close/focus, resize cleanup, Light/Dark và logout với CSRF giả lập. Ảnh desktop expanded/collapsed và mobile drawer được kiểm tra trực quan. Đây là browser automation với auth fixtures riêng trong phiên test, không chứng minh đăng nhập bằng tài khoản thực. QA thủ công trên thiết bị cảm ứng, screen reader, zoom 200% và trình duyệt khác còn pending. Tracker không có ID App Shell riêng nên giữ nguyên.

## Category Frontend Part 2 — 2026-11-21

CAT-001 bổ sung page thật tại mục Danh mục của App Shell đã xác thực; các ghi chú
coming-soon phía trên mô tả foundation trước đó, không áp dụng cho page Category mới.
Các module khác giữ nguyên placeholder. Không đọc role từ frontend để suy quyền.

`features/categories/` dùng PageHeader, Card, Button, TextInput, StatusBadge,
LoadingState và EmptyState hiện có. Table semantic có caption/column/row headers,
vùng cuộn riêng có tên và focus-visible; filter/layout co về một cột trên mobile.
Mọi màu dùng semantic tokens light/dark, không thêm palette hoặc UI framework.

Dialog tạo/sửa/xác nhận dùng native modal, label/error liên kết, focus đầu vào hoặc
nút Hủy, Escape/backdrop/Close, khóa cuộn và khôi phục focus. Không đóng khi mutation
đang chạy; chặn submit trùng và chặn submit dữ liệu cũ nếu detail read thất bại.
Ngừng sử dụng có xác nhận riêng; không thêm xóa hoặc kích hoạt lại.

API contract lấy từ Category Controller/DTO và category-management-api.md:
items/page/size/totalElements/totalPages, ID string, payload chỉ code/name/description.
API transport dùng chung với auth, cookie same-origin và CSRF mới cho mỗi mutation,
không retry tự động, không lưu token/session/password. Error chỉ dùng status/code và
tên field allowlist; không render detail/message thô từ Backend.

60/60 tests frontend PASS: 24 regression + 13 client/validation + 22 Category UI +
1 shell integration; npm run build PASS. Tests dùng API stubs trong test source,
không đưa fixture vào runtime. Browser render smoke với API intercept không thay
E2E thật; keyboard/native focus trap, thiết bị cảm ứng, screen reader, zoom/contrast
từng màn hình và cross-browser còn pending. HTTP Category vẫn denyAll; grants và
contract approval còn pending. Không sửa Backend/DB hoặc tự đánh dấu CAT-001 DONE.

## Quy ước bảng và ngày giờ — Category UX V1

- Thao tác trong ô bảng dùng nút có viền, cao tối thiểu 36 px, đệm ngang 12 px,
  bo góc 8 px và cách nhau 8 px. Căn đầu ô, giữ độ rộng theo nhãn và tránh ngắt
  chữ trong nút. Đặt các nút cùng hàng khi đủ chỗ. Khi hẹp, cho phép xuống hàng
  theo từng nút và giữ vùng cuộn ngang riêng của bảng.
- Dùng viền trung tính cho **Sửa** và viền đỏ dịu theo token lỗi cho
  **Ngừng sử dụng**. Giữ hover, focus-visible và trạng thái disabled của nút
  HTML. Danh mục đã ngừng sử dụng chỉ hiển thị **Sửa**.
- Badge **Đang sử dụng** dùng token `success` với nền dịu. Badge **Ngừng sử dụng**
  dùng token `error` với nền đỏ dịu. Giữ nhãn trạng thái rõ ràng và kiểm tra
  tương phản chữ trên cả giao diện sáng và tối.
- Hiển thị ngày theo `dd/MM/yyyy` và ngày giờ theo `HH:mm dd/MM/yyyy` ở múi giờ
  `Asia/Ho_Chi_Minh`. Dùng formatter hiển thị chung `formatVietnameseDate` và
  giữ timestamp gốc trong thuộc tính `dateTime`. Khi hiển thị cùng ô, đặt giờ
  cập nhật ở dòng chính, ngày tạo ở dòng phụ.
