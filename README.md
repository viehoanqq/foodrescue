# FoodRescue

Website giải cứu thực phẩm cuối ngày. Cửa hàng đăng "túi giải cứu" giảm 40-70% kèm khung giờ nhận hàng; khách tìm túi gần mình trên bản đồ, đặt và thanh toán qua VNPay, rồi đến quầy đọc mã nhận hàng.

- **4 vai trò:** Khách hàng · Nhân viên cửa hàng · Chủ cửa hàng · Quản trị viên
- **Công nghệ:** Spring Boot 3.3 (Java 17) · React 18 + Vite + Ant Design · MySQL / MariaDB (XAMPP)
- **Tài liệu:** thư mục [`docs/`](docs/) (tài liệu dự án, kế hoạch phân công, quy ước chung)

## Cấu trúc thư mục

```
foodrescue/
├── backend/    Spring Boot REST API (cổng 8080)
├── frontend/   React + Vite (cổng 5173)
├── database/   foodrescue_schema.sql (tạo CSDL + dữ liệu mẫu), foodrescue_checks.sql (kiểm tra dữ liệu)
└── docs/       tài liệu PDF
```

## Cài đặt

### 1. Chuẩn bị
Cài sẵn: **JDK 17+**, **Maven**, **Node.js 20+**, **Git**, **XAMPP**.

> Clone repo vào thư mục **không dấu, không khoảng trắng** (vd `E:\projects\foodrescue`). Đường dẫn có dấu tiếng Việt làm `mvn spring-boot:run` báo lỗi `Could not find or load main class`.

```powershell
git clone https://github.com/<tên-github>/foodrescue.git
cd foodrescue
git checkout develop
```

### 2. Tạo CSDL bằng XAMPP
1. Mở **XAMPP Control Panel**, bấm **Start** ở dòng **MySQL**.
2. Tạo CSDL, chọn **một** trong hai cách:
   - **phpMyAdmin:** mở `http://localhost/phpmyadmin` → tab **Import** → chọn `database/foodrescue_schema.sql` → **Import**.
   - **Dòng lệnh** (chạy trong thư mục repo):
     ```powershell
     cmd /c "C:\xampp\mysql\bin\mysql.exe -u root --default-character-set=utf8mb4 < database\foodrescue_schema.sql"
     ```
3. Kiểm tra: CSDL `foodrescuedb` có **13 bảng**.

Script xoá và tạo lại `foodrescuedb` mỗi lần chạy. Chạy lại khi muốn đưa dữ liệu về như ban đầu.

### 3. Chạy backend
```powershell
cd backend
copy src\main\resources\application-local.example.yml src\main\resources\application-local.yml
mvn spring-boot:run
```
XAMPP mặc định tài khoản `root` không có mật khẩu, nên để trống dòng `password:` trong `application-local.yml`. Nếu đã đặt mật khẩu thì điền vào. **Không commit file `application-local.yml`**.

Kiểm tra:
- `http://localhost:8080/api/ping` → `{"status":"ok",...}`
- `http://localhost:8080/swagger-ui.html` → danh sách API

### 4. Chạy frontend
Mở terminal thứ hai:
```powershell
cd frontend
npm install
npm run dev
```
Mở `http://localhost:5173`. Frontend tự chuyển các lời gọi `/api` sang backend cổng 8080, nên backend phải đang chạy.

## Tài khoản mẫu
Mật khẩu chung: **`123456`**

| Email | Vai trò |
|---|---|
| admin@foodrescue.vn | Quản trị viên |
| owner.anphat@gmail.com | Chủ cửa hàng (An Phát) |
| owner.sweetparis@gmail.com | Chủ cửa hàng (Sweet Paris) |
| owner.greenmart@gmail.com | Chủ cửa hàng đang chờ duyệt |
| staff.anphat@gmail.com | Nhân viên An Phát |
| an.malyhoang@gmail.com | Khách hàng |

## Lỗi thường gặp

| Lỗi | Cách xử lý |
|---|---|
| `Could not find or load main class` | Repo đang ở đường dẫn có dấu → chuyển sang thư mục không dấu |
| `Communications link failure` | MySQL trong XAMPP chưa Start |
| `Access denied for user 'root'` | Sai mật khẩu trong `application-local.yml` |
| `Unknown database 'foodrescuedb'` | Chưa chạy bước 2 |
| Port 3306 / 8080 / 5173 đã bị dùng | Tắt chương trình đang chiếm cổng (MySQL cài riêng, lần chạy cũ chưa tắt...) |
| VS Code gạch đỏ code Java dù Maven chạy được | `Ctrl+Shift+P` → **Java: Clean Java Language Server Workspace** |

## Làm việc nhóm
- Code trên nhánh riêng `feature/tv<số>-<mã việc>-<mô tả>` tách từ `develop`, xong thì mở Pull Request vào `develop`.
- Đặt tên, định dạng API, màu và giao diện theo `docs/FoodRescue_QuyUocChung.pdf`.
- Việc của từng người theo `docs/FoodRescue_KeHoachPhanCong.pdf`.
