# identity-service

Một ứng dụng Spring Boot, sở hữu MySQL `identity_db`. Các lớp nằm trong `api/`, `application/`, `domain/`, `infrastructure/`; Flyway migration ở `src/main/resources/db/migration/`.

Đã có đăng ký/đăng nhập, hồ sơ, đổi mật khẩu, quản trị user, audit có lọc, `GET /internal/users/{id}/status`, `GET /internal/users/count`, tạo và khóa/mở staff nội bộ. Thao tác identity ghi audit cùng transaction; listener `audit.action.v1` nhận audit từ các service khác và dùng Inbox để chống xử lý lặp.

## API nội bộ cho Store và Finance

Hai endpoint mới yêu cầu header `X-Internal-Key`; service gọi trực tiếp Identity, không đi qua Gateway.

- `GET /internal/users/count` trả `{ "activeCustomers": 12, "totalUsers": 20 }`. `activeCustomers` chỉ đếm `role=CUSTOMER` và `status=ACTIVE`; `totalUsers` đếm tất cả tài khoản, gồm cả tài khoản bị khóa.
- `POST /internal/staff` nhận `{ "actorId": 2, "storeId": 5, "email": "staff@example.com", "fullName": "Nhân viên", "phone": "0901234567" }`. Trả HTTP 201 với `userId`, thông tin staff, `role=STORE_STAFF`, `status=ACTIVE`, `mustChangePassword=true` và `temporaryPassword` được sinh ngẫu nhiên. Chỉ trả mật khẩu tạm khi tạo; Identity chỉ lưu bản băm. Email được chuẩn hóa chữ thường và kiểm tra trùng (HTTP 409, `EMAIL_EXISTS`).
- `POST /internal/staff/{id}/lock` và `/unlock` nhận `{ "actorId": 2, "storeId": 5 }`. Identity kiểm tra actor là `STORE_OWNER` đang hoạt động, user đích là staff của đúng `storeId`; nếu trạng thái đổi thì tăng `tokenVersion`, ghi audit và trả `{ "userId": 9, "storeId": 5, "status": "LOCKED", "tokenVersion": 1 }`. Gọi lại cùng trạng thái không tăng version.

Store phải xác nhận `actorId` là chủ đang được phép quản lý đúng `storeId` trước khi gọi. Identity kiểm tra actor còn `ACTIVE` và có role `STORE_OWNER`; không thể tự xác nhận quan hệ chủ/cửa hàng vì quan hệ này thuộc DB của Store. Tạo user và ghi `STORE_STAFF_CREATED` vào audit cùng transaction. Người dùng phải đổi mật khẩu trước khi gọi các API khác.

`GET /api/admin/audit-logs` nhận tùy chọn `actorId`, `action`, `entityType`, `entityId`, `from`, `to`, `page`, `size`. `from/to` là ISO-8601 **có offset**, ví dụ `2026-11-22T00:00:00+07:00`; cả hai mốc đều bao gồm trong kết quả. Mặc định `page=0`, `size=20` và `size` tối đa 100. Kết quả sắp xếp mới nhất trước, có `actorEmail` từ bảng `users`.

## Tài khoản demo local

`.env` có `SEED_ACCOUNTS_ENABLED=true`, `SEED_ADMIN_EMAIL`, `SEED_ADMIN_PASSWORD`, `SEED_OWNER_EMAIL`, `SEED_OWNER_PASSWORD`. Mẫu là `admin@gmail.com` và `owner@gmail.com`, mật khẩu `12345678`. Seeder chạy khi identity khởi động, chỉ thêm email còn thiếu; không đổi tài khoản có sẵn. Chủ cửa hàng mang role `STORE_OWNER`; hồ sơ cửa hàng tương ứng phải được tạo trong `store_db` bởi nghiệp vụ Store sau này.

Build: `mvn -f services/pom.xml -pl :identity-service -am package`.
