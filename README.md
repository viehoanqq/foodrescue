# FoodRescue

FoodRescue là nền tảng đặt **túi thực phẩm cuối ngày** từ các cửa hàng địa phương. Khách hàng tìm túi phù hợp, đặt trước và đến nhận trong khung giờ của cửa hàng. Chủ cửa hàng quản lý túi, đơn hàng và doanh thu; quản trị viên quản lý tài khoản, cửa hàng và hoạt động tài chính.

## Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Frontend | React 18, Vite, React Router, Ant Design, Axios |
| Backend | Java 17, Spring Boot 3.3, Spring Cloud Gateway, Spring Data JPA/JDBC |
| Cơ sở dữ liệu | MySQL 8.4, PostgreSQL 17, Flyway migration |
| Giao tiếp | REST nội bộ, RabbitMQ, Outbox/Inbox |
| Môi trường chạy | Docker Compose, Maven, npm |

Hệ thống có **5 service nghiệp vụ**, mỗi service sở hữu một database logic:

| Service | Database | Miền chức năng |
|---|---|---|
| `identity-service` | MySQL `identity_db` | Tài khoản, đăng nhập, phân quyền, nhật ký thao tác |
| `store-service` | MySQL `store_db` | Cửa hàng, nhân viên, đánh giá |
| `catalog-inventory-service` | PostgreSQL `catalog_db` | Danh mục theo cửa hàng, túi giải cứu, tồn kho |
| `order-service` | PostgreSQL `order_db` | Giỏ hàng, đơn hàng, mã nhận hàng |
| `payment-finance-service` | PostgreSQL `finance_db` | Thanh toán, hoàn tiền, chi trả, thống kê |

`api-gateway` là điểm vào API; `building-blocks` chứa các cơ chế Java dùng chung giữa service. Frontend hiện có dữ liệu mẫu trong trình duyệt để xem và thao tác các trang nghiệp vụ; đăng nhập và quản lý tài khoản dùng `identity-service`.

## Chạy trên Windows

Cài **Docker Desktop**, **JDK 17**, **Maven**, **Node.js** và **npm**. Trong thư mục gốc của repository:

```powershell
Copy-Item .env.example .env
powershell -ExecutionPolicy Bypass -File scripts/restart-services.ps1
```

Chỉnh các mật khẩu và secret trong `.env` theo máy local. Script bật MySQL, PostgreSQL, RabbitMQ bằng Docker, build các service và mở terminal riêng cho từng service, API Gateway và frontend. Chạy lại cùng lệnh để khởi động lại. Nếu Docker volume đã được tạo trước đó, mật khẩu DB/RabbitMQ trong `.env` phải khớp với tài khoản trong volume.

`FINANCE_STORE_API_KEY` là khóa riêng để Finance đọc tài khoản nhận tiền từ Store; đặt khác `INTERNAL_API_KEY`. Các API `/internal/stores/**` không được định tuyến qua Gateway.

| Địa chỉ | Nội dung |
|---|---|
| `http://localhost:5173` | Giao diện web |
| `http://localhost:8080` | API Gateway |
| `http://localhost:8080/api/health` | Kiểm tra 5 service và DB |
| `http://localhost:15672` | RabbitMQ Management |

## Swagger UI

Sau khi các service khởi động, mở Swagger UI trực tiếp trên cổng của từng service:

| Service | Swagger UI | OpenAPI JSON |
|---|---|---|
| Identity | `http://localhost:8082/swagger-ui.html` | `http://localhost:8082/v3/api-docs` |
| Store | `http://localhost:8083/swagger-ui.html` | `http://localhost:8083/v3/api-docs` |
| Catalog & Inventory | `http://localhost:8084/swagger-ui.html` | `http://localhost:8084/v3/api-docs` |
| Order | `http://localhost:8085/swagger-ui.html` | `http://localhost:8085/v3/api-docs` |
| Payment & Finance | `http://localhost:8086/swagger-ui.html` | `http://localhost:8086/v3/api-docs` |

Swagger hiển thị các endpoint **đã có trong code** của từng service. Gọi `/api/auth/login` ở Identity lấy JWT rồi bấm **Authorize** trong Swagger của service cần thử và nhập token. API `/internal/**` yêu cầu `X-Internal-Key` khi endpoint có khai báo header này; chỉ dùng để thử tích hợp giữa các service trong môi trường local. Swagger không đi qua API Gateway.

Các API `/api/**` có Bearer token ở Store, Catalog, Order và Finance xác minh chữ ký/hạn token rồi gọi Identity lấy `status`, `role`, `tokenVersion`, `mustChangePassword` và `fullName` hiện tại. Nếu Identity không sẵn sàng, API cần đăng nhập trả 503. Quyền sở hữu store/order vẫn do service chứa dữ liệu kiểm tra. Frontend gửi JWT qua Gateway; Gateway đã khai báo route cho cả 5 service. Sau khi Identity đổi cách ký cố định sang HS256, hãy đăng nhập lại để lấy token mới.

Nếu muốn chạy backend bằng Docker Compose:

```powershell
docker compose --env-file .env --profile services --profile gateway up -d --build
```

Compose không chạy frontend. Mở thêm terminal:

```powershell
cd frontend
npm ci
npm run dev
```

## Tài khoản mẫu

Khi `SEED_ACCOUNTS_ENABLED=true` trong `.env`, `identity-service` tạo tài khoản mẫu lúc khởi động nếu email chưa tồn tại. Giá trị trong `.env.example`:

| Vai trò | Email | Mật khẩu | Trang đăng nhập |
|---|---|---|---|
| Quản trị viên | `admin@gmail.com` | `12345678` | `/admin/login` |
| Chủ cửa hàng | `owner@gmail.com` | `12345678` | `/store/login` |

Khách hàng có thể đăng ký tại `/register`. Tài khoản mẫu đã tồn tại sẽ không bị đặt lại mật khẩu khi khởi động lại. Không có tài khoản nhân viên mẫu; trang quản lý nhân viên hiện dùng dữ liệu demo trong trình duyệt.

## Cây thư mục

```text
foodrescue/
├── frontend/                         # React, trang, route, dữ liệu mẫu
│   └── src/
│       ├── auth/                     # Phiên đăng nhập
│       ├── demo/                     # Dữ liệu nghiệp vụ mẫu
│       ├── layouts/                  # Bố cục chung
│       ├── pages/                    # Trang khách, cửa hàng, admin
│       ├── routes/                   # React Router và bảo vệ route
│       └── services/                 # HTTP client và identity API
├── services/
│   ├── api-gateway/
│   ├── building-blocks/
│   ├── identity-service/
│   ├── store-service/
│   ├── catalog-inventory-service/
│   ├── order-service/
│   └── payment-finance-service/
├── contracts/                        # Hợp đồng HTTP, event, DB và phân công
├── docs/                             # Kiến trúc và hướng dẫn Docker
├── docker/                           # Khởi tạo database
├── scripts/                          # Script chạy local
├── docker-compose.yml
└── .env.example
```

Chi tiết: [kiến trúc hệ thống](https://github.com/viehoanqq/foodrescue/blob/main/docs/KienTruc_Microservices_Outbox_RabbitMQ.md)
