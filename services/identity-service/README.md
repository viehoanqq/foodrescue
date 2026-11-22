# identity-service

Một ứng dụng Spring Boot, sở hữu MySQL `identity_db`. Các lớp nằm trong `api/`, `application/`, `domain/`, `infrastructure/`; Flyway migration ở `src/main/resources/db/migration/`.

Đã có đăng ký/đăng nhập, hồ sơ, đổi mật khẩu, quản trị user, audit và `GET /internal/users/{id}/status`. Thao tác identity ghi audit cùng transaction; listener `audit.action.v1` nhận audit từ các service khác và dùng Inbox để chống xử lý lặp.

## Tài khoản demo local

`.env` có `SEED_ACCOUNTS_ENABLED=true`, `SEED_ADMIN_EMAIL`, `SEED_ADMIN_PASSWORD`, `SEED_OWNER_EMAIL`, `SEED_OWNER_PASSWORD`. Mẫu là `admin@gmail.com` và `owner@gmail.com`, mật khẩu `12345678`. Seeder chạy khi identity khởi động, chỉ thêm email còn thiếu; không đổi tài khoản có sẵn. Chủ cửa hàng mang role `STORE_OWNER`; hồ sơ cửa hàng tương ứng phải được tạo trong `store_db` bởi nghiệp vụ Store sau này.

Build: `mvn -f services/pom.xml -pl :identity-service -am package`.
