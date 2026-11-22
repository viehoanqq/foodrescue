# Java building blocks dùng chung

`building-blocks` là **module runtime**, khác với `contracts/` là tài liệu và JSON Schema. Service phụ thuộc module này qua Maven reactor `services/pom.xml`.

Hiện module cung cấp:

- `InternalServiceClient`: HTTP client nội bộ, tự gắn `X-Internal-Key` và `X-Correlation-Id`, connect timeout 1 giây, read timeout 3 giây; phân biệt lỗi HTTP qua `InternalServiceException`.
- `DatabaseHealthController`: mỗi business service import lớp này để mở `GET /internal/health/db`, kiểm tra kết nối DB của chính service đó. `api-gateway` gọi 5 endpoint này tại `GET /api/health`.
- `InternalKeyVerifier`: kiểm tra key ở API nội bộ bằng so sánh constant-time. Đây là giải pháp cho môi trường phát triển, sẽ đổi sang service JWT/mTLS trước khi triển khai thật.
- `EventEnvelope<T>` và `EventEnvelope.create(...)`: kiểu chung và cách tạo metadata sự kiện v1, kiểm tra các field bắt buộc; payload `T` do service owner định nghĩa theo JSON Schema trong `contracts/`. Catalog đã dùng mẫu này khi ghi event giữ/trả kho vào Outbox.

Các lớp này không chứa entity JPA, repository hoặc quy tắc nghiệp vụ. Mỗi service định nghĩa `Port` trong `application`, adapter HTTP trong `infrastructure`, controller trong `api`, DTO nghiệp vụ trong `domain` hoặc `api`. Không gọi trực tiếp `InternalServiceClient` từ domain.

Ví dụ thật: `order-service` có `InventoryPort` và `CatalogInventoryAdapter` dùng `InternalServiceClient` để gọi `catalog-inventory-service`. Đường `POST /internal/order/reservations` dùng adapter này và là **đường tích hợp nội bộ**, chưa phải `POST /api/orders`.

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
