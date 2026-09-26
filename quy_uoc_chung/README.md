# Bộ file dùng chung FoodRescue

TV1 chép các file này vào repo **ngày 1**, trước khi ai khác bắt đầu code. Sau đó mọi người dùng lại,
không tự viết bản riêng. Muốn sửa: tạo PR, gắn nhãn `shared-change`, báo nhóm.
Giải thích đầy đủ trong `FoodRescue_QuyUocChung.pdf`.

| File trong bộ này | Chép vào repo | Dùng để |
|---|---|---|
| `.editorconfig`, `.gitattributes` | thư mục gốc repo | Cùng kiểu thụt lề, mã hoá UTF-8, xuống dòng LF |
| `.github/pull_request_template.md` | `.github/` | Checklist mỗi Pull Request |
| `backend/src/main/java/com/foodrescue/common/enums/*` | giữ nguyên đường dẫn | 13 enum trạng thái, trùng đúng giá trị trong CSDL |
| `backend/.../common/api/ApiError.java`, `PageResponse.java` | giữ nguyên | Định dạng lỗi và danh sách phân trang của mọi API |
| `backend/.../common/exception/*` | giữ nguyên | `ErrorCode` (mã lỗi + thông báo tiếng Việt), `BusinessException`, `GlobalExceptionHandler` |
| `backend/.../common/util/Money.java` + `MoneyTest.java` | giữ nguyên | Tính phí, thực nhận, kiểm tra giá, khớp CHECK trong CSDL |
| `backend/.../common/config/TimeZoneConfig.java` | giữ nguyên | Chạy theo giờ Việt Nam |
| `backend/src/main/resources/application.yml` | giữ nguyên | Cấu hình chung; mật khẩu và key để trong `application-local.yml` (không commit) |
| `frontend/.prettierrc.json` | `frontend/` | Cùng kiểu format code React |
| `frontend/src/shared/theme/theme.js` | giữ nguyên | Màu, font, bo góc, khoảng cách, theme Ant Design |
| `frontend/src/shared/constants/status.js` | giữ nguyên | Nhãn tiếng Việt + màu cho mọi trạng thái |
| `frontend/src/shared/utils/format.js` | giữ nguyên | Hiện tiền, ngày giờ, khung giờ lấy, khoảng cách |
| `frontend/src/shared/api/axios.js` | giữ nguyên | Gắn JWT, chuẩn hoá lỗi, chuyển trang khi 401 / cần đổi mật khẩu |

Đã kiểm tra: phần backend biên dịch được với Spring Boot 3.3.5 (JDK 21) và `MoneyTest` pass 4/4;
phần frontend qua kiểm tra cú pháp Node 24, các hàm format cho kết quả đúng (vd `formatMoney(45000)` → `45.000đ`).

Frontend cần cài: `npm i antd @ant-design/icons axios react-router-dom dayjs` và font
Be Vietnam Pro (thêm vào `index.html`):

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@400;500;600;700&display=swap" rel="stylesheet">
```
