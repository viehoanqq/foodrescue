# FoodRescue

![CI](https://github.com/viehoanqq/foodrescue/actions/workflows/ci.yml/badge.svg?branch=develop)

Website **giải cứu thực phẩm cuối ngày**. Cửa hàng đăng "túi giải cứu" giảm 40-70% kèm khung giờ nhận hàng; khách tìm túi gần mình trên bản đồ, cho vào giỏ, thanh toán qua VNPay, rồi đến quầy đọc mã nhận hàng. Nền tảng thu phí trên mỗi đơn và chi trả phần còn lại cho cửa hàng theo kỳ.

Đồ án môn Lập trình J2EE.

## Chức năng chính

| Vai trò | Làm được gì |
|---|---|
| **Khách hàng** | Tìm túi theo khoảng cách, xem bản đồ, giỏ hàng, thanh toán VNPay Sandbox, xem mã nhận hàng / QR, đánh giá |
| **Nhân viên cửa hàng** | Màn hình quầy, nhập mã giao hàng, thêm / bớt túi, xem sổ kho |
| **Chủ cửa hàng** | Đăng ký cửa hàng, đăng / sửa / huỷ túi, huỷ đơn, tài khoản nhân viên, doanh thu, sao kê, yêu cầu rút tiền |
| **Quản trị viên** | Duyệt cửa hàng và đặt phí, người dùng, danh mục, đơn hàng, hoàn tiền, chi trả theo kỳ, đối soát, nhật ký, thống kê |

## Công nghệ

| Phần | Công nghệ |
|---|---|
| Backend | Java 17, Spring Boot 3.3 (Web, Data JPA, Security, Validation), JWT (jjwt), Swagger (springdoc), Maven |
| Frontend | React 18, Vite, Ant Design 5, React Router, Axios, React Leaflet, Recharts |
| CSDL | MySQL 8 / MariaDB 10.4+ (XAMPP) |
| Tích hợp | VNPay Sandbox (thanh toán), OSRM + OpenStreetMap (bản đồ, khoảng cách) |
| CI | GitHub Actions: build, test backend với MySQL 8; lint, build frontend |

Kiến trúc: **frontend React** gọi **REST API** của backend (JSON). Backend chia tầng **Controller → Service → Repository** và chia mã nguồn theo module nghiệp vụ.

## Cấu trúc thư mục

```
foodrescue/
├── .github/
│   ├── workflows/ci.yml            CI chạy mỗi lần push / mở Pull Request
│   └── pull_request_template.md    checklist khi mở Pull Request
├── backend/                        Spring Boot, cổng 8080
│   └── src/main/java/com/foodrescue/
│       ├── common/                 dùng chung: api, config, entity, enums, exception, repository,
│       │                           security, service (sổ kho, nhật ký, hoàn tiền), storage, util
│       ├── auth/  admin/           TV1
│       ├── store/  batch/  adminuser/        TV2
│       ├── catalog/  review/  admincatalog/  TV3
│       └── cart/  order/  payment/  pickup/  scheduler/   TV4
├── frontend/                       React + Vite, cổng 5173
│   └── src/
│       ├── app/  layouts/          khung ứng dụng, 3 layout (khách, cửa hàng, admin)
│       ├── features/               mỗi module một thư mục (auth, admin, store, catalog, order, ...)
│       └── shared/                 dùng chung: api (axios), theme, constants (trạng thái), utils (format), components
├── database/
│   ├── foodrescue_schema.sql       tạo CSDL 13 bảng + dữ liệu mẫu
│   └── foodrescue_checks.sql       câu truy vấn kiểm tra dữ liệu (tiền, kho, đơn)
└── docs/                           tài liệu dự án, kế hoạch phân công, quy ước chung (PDF)
```

## Cài đặt và chạy

### 1. Chuẩn bị
Cài sẵn: **JDK 17+**, **Maven**, **Node.js 20.19+** (khuyên dùng 22), **Git**, **XAMPP**, **VS Code**.

> ⚠️ Clone repo vào thư mục **không dấu, không khoảng trắng** (vd `E:\projects\foodrescue`). Đường dẫn có dấu tiếng Việt làm `mvn spring-boot:run` báo lỗi `Could not find or load main class`.

```powershell
git clone https://github.com/viehoanqq/foodrescue.git
cd foodrescue
git checkout develop
```

### 2. Tạo CSDL bằng XAMPP
1. Mở **XAMPP Control Panel**, bấm **Start** ở dòng **MySQL**.
2. Tạo CSDL, chọn **một** trong hai cách:
   - **phpMyAdmin:** mở `http://localhost/phpmyadmin` → tab **Import** → chọn `database/foodrescue_schema.sql` → **Import**.
   - **Dòng lệnh** (đứng ở thư mục repo):
     ```powershell
     cmd /c "C:\xampp\mysql\bin\mysql.exe -u root --default-character-set=utf8mb4 < database\foodrescue_schema.sql"
     ```
3. Kiểm tra: CSDL `foodrescuedb` có **13 bảng**.

Script **xoá và tạo lại** `foodrescuedb` mỗi lần chạy. Chạy lại khi muốn đưa dữ liệu về như ban đầu.

### 3. Chạy backend
```powershell
cd backend
copy src\main\resources\application-local.example.yml src\main\resources\application-local.yml
mvn spring-boot:run
```
- `application-local.yml` là cấu hình riêng của máy bạn (mật khẩu MySQL, key). XAMPP mặc định `root` không có mật khẩu nên để trống `password:`. **Không commit file này** (đã có trong `.gitignore`).
- Kiểm tra:
  - `http://localhost:8080/api/ping` → `{"status":"ok",...}`
  - `http://localhost:8080/swagger-ui.html` → danh sách API
  - `http://localhost:8080/api/orders` → lỗi `UNAUTHORIZED` (đúng, vì chưa đăng nhập)

### 4. Chạy frontend
Mở terminal thứ hai:
```powershell
cd frontend
npm install
npm run dev
```
Mở `http://localhost:5173`. Vite tự chuyển các lời gọi `/api`, `/uploads` sang backend cổng 8080, nên **backend phải đang chạy**.

### Lệnh hay dùng

| Lệnh | Chạy ở | Tác dụng |
|---|---|---|
| `mvn spring-boot:run` | `backend/` | Chạy backend |
| `mvn verify` | `backend/` | Build + chạy toàn bộ test (giống CI) |
| `npm run dev` | `frontend/` | Chạy frontend (tự tải lại khi sửa code) |
| `npm run lint` | `frontend/` | Kiểm tra lỗi code frontend |
| `npm run build` | `frontend/` | Build bản chạy thật vào `frontend/dist` |

## Tài khoản mẫu
Mật khẩu chung: **`123456`**

| Email | Vai trò | Dữ liệu có sẵn |
|---|---|---|
| admin@foodrescue.vn | Quản trị viên | 1 cửa hàng chờ duyệt, 1 yêu cầu hoàn tiền, 1 cửa hàng còn tiền chưa chi |
| owner.anphat@gmail.com | Chủ cửa hàng (An Phát) | Có đơn đã giao, đánh giá, kỳ chi trả đã chi |
| owner.sweetparis@gmail.com | Chủ cửa hàng (Sweet Paris) | Có đơn chờ nhận, đơn đã huỷ, đơn chờ thanh toán |
| owner.comtam365@gmail.com | Chủ cửa hàng (Cơm Tấm 365) | Có đơn khách không đến lấy, 18.700đ chưa chi |
| owner.greenmart@gmail.com | Chủ cửa hàng (GreenMart) | Cửa hàng đang chờ duyệt |
| staff.anphat@gmail.com | Nhân viên An Phát | |
| an.malyhoang@gmail.com | Khách hàng | Có đơn 2 loại túi đang chờ nhận, mã `H2R9W7` |
| linh.tranhoang@gmail.com | Khách hàng | Giỏ hàng có sẵn 2 loại túi |

## Làm việc nhóm

### Phân công module

| Thành viên | Module | Backend | Frontend |
|---|---|---|---|
| TV1 | Nền tảng, tài khoản, tiền (nhóm trưởng) | `common/`, `auth/`, `admin/` | `app/`, `layouts/`, `shared/`, `features/auth`, `features/admin` |
| TV2 | Cửa hàng, túi, kho | `store/`, `batch/`, `adminuser/` | `features/store`, `features/admin-stores`, `features/admin-users` |
| TV3 | Khách hàng, bản đồ, giao diện chung | `catalog/`, `review/`, `admincatalog/` | `features/catalog`, `features/review`, `features/admin-catalog`, `shared/components` |
| TV4 | Giỏ hàng, đơn, thanh toán, giao hàng | `cart/`, `order/`, `payment/`, `pickup/`, `scheduler/` | `features/order` |

Mỗi người chỉ sửa trong thư mục của mình. Sửa phần dùng chung (`common/`, `shared/`, `database/`, `pom.xml`, `package.json`) thì mở Pull Request riêng, gắn nhãn `shared-change`, báo cả nhóm. Chi tiết việc từng tuần: `docs/FoodRescue_KeHoachPhanCong.pdf`.

### Nhánh Git

| Nhánh | Dùng để |
|---|---|
| `main` | Bản ổn định, chỉ nhận merge từ `develop` khi qua mỗi cổng kiểm tra (gắn tag `v0.1`, `v0.3`, ...) |
| `develop` | Nhánh tích hợp, mọi Pull Request đều vào đây |
| `feature/tv<số>-<mã việc>-<mô tả>` | Nhánh làm việc của từng người, vd `feature/tv4-O12-cart` |
| `fix/...`, `chore/...` | Sửa lỗi, việc lặt vặt (cấu hình, tài liệu) |

### Quy trình mỗi việc
```powershell
git checkout develop
git pull
git checkout -b feature/tv4-O12-cart
# ... code, test ở máy ...
git add .
git commit -m "feat(cart): API giỏ hàng"
git push -u origin feature/tv4-O12-cart
```
1. Push lên nhánh của mình → **CI tự chạy** (xem ✅/❌ ở tab **Actions** hoặc cạnh commit).
2. Mở **Pull Request** vào `develop`, điền checklist có sẵn → CI chạy lại trên code đã ghép với `develop`.
3. **CI xanh + 1 người duyệt** thì mới merge được.
4. Merge xong, xoá nhánh; mọi người `git pull` trên `develop`.

Commit theo dạng `loại(module): mô tả`, vd `feat(order): tạo đơn từ giỏ`, `fix(catalog): sửa thứ tự toạ độ OSRM`, `docs: cập nhật README`. Các loại: `feat`, `fix`, `ui`, `test`, `docs`, `chore`, `ci`, `db`.

### CI (GitHub Actions)
File `.github/workflows/ci.yml`, chạy khi **push lên bất kỳ nhánh nào** và khi **mở / cập nhật Pull Request** vào `develop`, `main`:

| Job | Các bước |
|---|---|
| **Backend (build + test)** | Bật MySQL 8 → chạy `foodrescue_schema.sql` → kiểm tra chênh lệch đối soát bằng 0 → `mvn verify` |
| **Frontend (lint + build)** | `npm ci` → `npm run lint` → `npm run build` |

Trước khi push nên chạy `mvn verify` và `npm run build` ở máy để CI không bị đỏ. CI chạy trên Linux nên **phân biệt hoa thường** ở tên file (`OrderService.java` khác `orderService.java`).

### Quy ước
Đặt tên (bảng, class, hàm, URL, JSON), định dạng lỗi API, màu, font, cách hiện tiền và ngày: xem `docs/FoodRescue_QuyUocChung.pdf`. Luôn dùng các file dùng chung thay vì tự viết lại:
- Backend: `ErrorCode` và `BusinessException` cho lỗi, `Money` cho tính tiền, các enum trong `common/enums`.
- Frontend: `theme.js` cho màu và font, `status.js` cho nhãn trạng thái, `format.js` cho tiền và ngày giờ, `api` từ `shared/api/axios.js` để gọi API.

## Kiểm tra dữ liệu
Sau khi test các chức năng liên quan đến tiền, kho, đơn hàng, chạy:
```powershell
cmd /c "C:\xampp\mysql\bin\mysql.exe -u root --default-character-set=utf8mb4 < database\foodrescue_checks.sql"
```
Dữ liệu đúng khi: câu đầu tiên có cột `chenh_lech = 0`, các câu kiểm tra khác **không trả về dòng nào**.

## Lỗi thường gặp

| Lỗi | Cách xử lý |
|---|---|
| `Could not find or load main class` | Repo đang ở đường dẫn có dấu → chuyển sang thư mục không dấu |
| `Communications link failure` | MySQL trong XAMPP chưa Start |
| `Access denied for user 'root'` | Sai mật khẩu trong `application-local.yml` |
| `Unknown database 'foodrescuedb'` | Chưa chạy bước 2 |
| Port 3306 / 8080 / 5173 đã bị dùng | Tắt chương trình đang chiếm cổng (MySQL cài riêng, lần chạy cũ chưa tắt...) |
| Frontend báo "Không kết nối được máy chủ" | Backend chưa chạy hoặc đang lỗi |
| VS Code gạch đỏ code Java dù Maven chạy được | `Ctrl+Shift+P` → **Java: Clean Java Language Server Workspace** |
| CI đỏ ở bước `npm ci` | Chưa commit `frontend/package-lock.json` sau khi cài thư viện |
| CI đỏ ở bước kiểm tra đối soát | Dữ liệu mẫu trong `foodrescue_schema.sql` bị sửa làm lệch tiền → chạy `foodrescue_checks.sql` ở máy để tìm chỗ sai |

## Tài liệu
- `docs/FoodRescue_TaiLieuDuAn.pdf`: phân tích, chức năng, quy tắc nghiệp vụ, API, CSDL, test case
- `docs/FoodRescue_KeHoachPhanCong.pdf`: kế hoạch 8 tuần, việc của từng người, cổng kiểm tra
- `docs/FoodRescue_QuyUocChung.pdf`: quy ước đặt tên, API, giao diện
