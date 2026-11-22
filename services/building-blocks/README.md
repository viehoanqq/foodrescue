# Java building blocks dùng chung

`building-blocks` là **module runtime**, khác với `contracts/` là tài liệu và JSON Schema. Service phụ thuộc module này qua Maven reactor `services/pom.xml`.

Hiện module cung cấp:

- `InternalServiceClient`: HTTP client nội bộ, tự gắn `X-Internal-Key` và `X-Correlation-Id`, connect timeout 1 giây, read timeout 3 giây; phân biệt lỗi HTTP qua `InternalServiceException`.
- `DatabaseHealthController`: mỗi business service import lớp này để mở `GET /internal/health/db`, kiểm tra kết nối DB của chính service đó. `api-gateway` gọi 5 endpoint này tại `GET /api/health`.
- `InternalKeyVerifier`: kiểm tra key ở API nội bộ bằng so sánh constant-time. Đây là giải pháp cho môi trường phát triển, sẽ đổi sang service JWT/mTLS trước khi triển khai thật.
- `EventEnvelope<T>` và `EventEnvelope.create(...)`: kiểu chung và cách tạo metadata sự kiện v1, kiểm tra các field bắt buộc; payload `T` do service owner định nghĩa theo JSON Schema trong `contracts/`. Catalog đã dùng mẫu này khi ghi event giữ/trả kho vào Outbox.
- `AccessTokenVerifier`, `IdentityStatusClient`, `BearerUserFilter`, `AuthenticatedUser`: xác minh JWT `sub/ver` tại service, gọi Identity lấy trạng thái và role hiện tại, rồi đưa principal đáng tin cậy vào Spring Security. Mỗi service tự khai báo route công khai, role và Swagger trong `infrastructure/config/*Security.java`.

Các lớp này không chứa entity JPA, repository hoặc quy tắc nghiệp vụ. Mỗi service định nghĩa `Port` trong `application`, adapter HTTP trong `infrastructure`, controller trong `api`, DTO nghiệp vụ trong `domain` hoặc `api`. Không gọi trực tiếp `InternalServiceClient` từ domain.

Ví dụ thật: `order-service` có `InventoryPort` và `CatalogInventoryAdapter` dùng `InternalServiceClient` để gọi `catalog-inventory-service`. Đường `POST /internal/order/reservations` dùng adapter này và là **đường tích hợp nội bộ**, chưa phải `POST /api/orders`.

Controller của API cần đăng nhập dùng `@AuthenticationPrincipal AuthenticatedUser user` để lấy `user.id()`, `user.role()`, `user.fullName()` và `user.storeId()`. `storeId` chỉ gắn cho `STORE_STAFF`; với `STORE_OWNER`, Store phải đối chiếu `stores.owner_id`. Không lấy actor ID hoặc role từ request body/header do browser tự đặt. Filter gọi `GET /internal/users/{id}/status` trên mỗi API request có Bearer token; Identity không sẵn sàng thì trả 503, token sai/hết hạn/đã bị thu hồi thì trả 401, account bị khóa hoặc bắt đổi mật khẩu thì trả 403.

Build toàn bộ:

```powershell
mvn -f services/pom.xml verify
```

Build riêng một service cùng building blocks:

```powershell
mvn -f services/pom.xml -pl :order-service -am package
```

Khi cần thêm tiện ích dùng chung, chỉ thêm cơ chế kỹ thuật thật sự giống nhau ở nhiều service. Thay đổi API/event wire format phải sửa `contracts/` và có review producer/consumer; không chỉ đổi Java record trong module này.

Lộ trình chuẩn hóa Outbox, Inbox và Rabbit topology nằm ở [quy định chung](../../contracts/quyet-dinh-tich-hop-v1.md#7-api-kỹ-thuật-chung-sẽ-đưa-vào-building-blocks). Chưa thêm DTO nghiệp vụ hoặc consumer của từng service vào module chung.

Xem [mẫu code và hợp đồng event](../../contracts/quyet-dinh-tich-hop-v1.md#9-mẫu-code-và-file-hợp-đồng) để tạo envelope, ghi Outbox và xử lý Inbox. `EventEnvelope.create(...)` không tự ghi Outbox hoặc publish RabbitMQ; publisher/consumer hiện vẫn do từng service triển khai.
