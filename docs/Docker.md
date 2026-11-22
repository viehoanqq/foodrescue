# Chạy FoodRescue bằng Docker

## Chuẩn bị

Tại thư mục gốc, sao chép `.env.example` thành `.env` rồi chỉnh secret/mật khẩu. Nếu đã có Docker volume cũ, sửa mật khẩu trong `.env` sẽ không tự đổi mật khẩu tài khoản DB đã khởi tạo.

## Chạy toàn bộ bằng Docker Compose

```powershell
docker compose --env-file .env --profile services --profile gateway up -d --build
```

MySQL chứa `identity_db`, `store_db`; PostgreSQL chứa `catalog_db`, `order_db`, `finance_db`. RabbitMQ ở cổng 5672, giao diện quản trị ở `http://localhost:15672`. Gateway ở `http://localhost:8080`; `GET /api/health` tổng hợp sức khỏe DB của 5 service.

## Chạy service ở các terminal riêng trên Windows

```powershell
powershell -ExecutionPolicy Bypass -File scripts/restart-services.ps1
```

Script khởi động MySQL, PostgreSQL, RabbitMQ bằng Docker, build Maven reactor và mở terminal cho 5 service, Gateway, frontend. Frontend ở `http://localhost:5173`. Script truyền biến từ `.env` cho từng service.

## Phạm vi hiện tại

Gateway chỉ định tuyến các API identity đã triển khai. Các màn hình nghiệp vụ mới dùng dữ liệu demo trong `frontend/src/demo/` và chưa gọi API Store/Catalog/Order/Finance. `/internal/**` không mở qua Gateway. Mỗi service chạy Flyway migration của DB mình; Hibernate dùng `ddl-auto: validate`.

MongoDB chỉ ở profile `search` tùy chọn, chưa sở hữu dữ liệu nghiệp vụ. Dùng `--profile search` nếu cần bật container này.
