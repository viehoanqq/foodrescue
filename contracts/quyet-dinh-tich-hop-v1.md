# Quyết định tích hợp v1 cho 4 người

> **Hợp đồng chung duy nhất cho 5 service**, ngày 22/11/2026. Phần đánh dấu “cần migration/code” chưa tồn tại trong runtime. Khi cần đổi, sửa hợp đồng + JSON Schema trước, producer và consumer cùng review. Căn cứ: migration V1/V2, `EventEnvelope`, `JdbcReservationAdapter`, `AuditEventListener` và các `OutboxPublisher` hiện có.

## 1. Ranh giới và thứ tự sử dụng HTTP/event

| Việc | Cách giao tiếp chốt | Lý do |
|---|---|---|
| Order giữ/trả kho | HTTP nội bộ tới Catalog, idempotent bằng `reservationId` | Cần kết quả ngay để trả checkout; event stock chỉ là thông báo đã xảy ra |
| Order kiểm tra Store APPROVED và chụp commission | HTTP `GET /internal/stores/{id}/checkout-policy` | Không đặt hàng từ cache status có thể cũ |
| Browser lấy URL VNPay | HTTP Finance; Finance xác minh payment intent với Order | Phải trả URL ngay; **không dùng `order.payment-requested.v1`** |
| VNPay IPN cập nhật payment | Finance commit payment + Outbox, rồi `payment.*` cho Order | IPN có thể đến lặp/muộn, Order tự xử lý state |
| Order hủy đơn đã trả tiền | `order.refund-required.v1` cho Finance | Yêu cầu hoàn rõ ràng, không suy đoán từ snapshot |
| Finance báo refund | `refund.*` cho Order | Order cập nhật paymentStatus hiển thị |
| Admin duyệt Store | Store commit status + `store.status-changed.v1` | Identity cấp role sau khi duyệt; không phát event “yêu cầu admin duyệt” |
| Review eligibility và payout account | HTTP nội bộ tới owner DB | Cần dữ liệu hiện hành/nhạy cảm; không broadcast event |

## 2. Envelope và transport bắt buộc

`EventEnvelope<T>` trong `building-blocks` là kiểu Java chung. JSON tuân [event-envelope.schema.json](schemas/event-envelope.schema.json): `eventId`, `eventType`, `schemaVersion=1`, `aggregateType`, `aggregateId` chuỗi, `aggregateVersion≥1`, `occurredAt` UTC, `correlationId`, `causationId`, `actorId`, `payload` object. `eventId` không đổi khi retry; mỗi thay đổi nghiệp vụ có ID mới. `actorId=null` cho IPN/scheduler; request người dùng lấy actor từ JWT. `aggregateVersion` tăng theo aggregate nguồn, không dùng thời gian làm version. Tiền VND là JSON integer, Java `BigDecimal` scale 0; hoa hồng là decimal phần trăm 0–50.

RabbitMQ dùng **topic exchange durable** `foodrescue.events`; routing key chính xác bằng `eventType`. Queue durable riêng theo consumer; producer publish persistent JSON (`messageId=eventId`, `contentType=application/json`) và chờ confirm trước khi đánh dấu `published_at`. Queue chính dùng DLX **direct** `foodrescue.dlx`, dead-letter routing key đúng tên queue; DLQ tên `<queue>.dead`. Retry có backoff hữu hạn, sau ngưỡng vào DLQ và cảnh báo. **Không bind `#` vào business queue**; queue quan sát `foodrescue.migration-events` chỉ phục vụ giai đoạn local và không phải bảo đảm lưu event.

Outbox/Inbox đã có trong cả năm DB, nhưng SQL MySQL/PostgreSQL khác nhau. Cột `payload` chứa **toàn bộ envelope JSON**, các cột riêng phải khớp JSON. Trong transaction nguồn: đổi business row + INSERT Outbox; publisher chạy sau commit. Trong transaction consumer: INSERT Inbox `(consumer_name,event_id)` + đổi DB/projection + INSERT Outbox phát tiếp nếu cần; ACK sau commit. Duplicate ACK không tác động lại DB. Event sai schema/type hoặc retry hết hạn vào DLQ, không bỏ qua âm thầm. Consumer dùng business unique key thêm ngoài Inbox.

**Hiện code chưa đạt toàn bộ transport trên:** các `OutboxPublisher` riêng vẫn giữ DB transaction trong lúc đợi Rabbit confirm; publisher MySQL còn dùng `CAST(? AS UUID)` của PostgreSQL; chưa lưu `attempts/next_attempt_at/last_error` khi lỗi; topology audit hiện dead-letter về topic exchange chính và queue `identity.audit-dead` thay vì DLX direct/`identity.audit-events.dead`; chưa có retry hữu hạn cho mọi queue. Đây là việc chung bắt buộc trước khi bật producer mới. Đổi DLQ audit cần kế hoạch drain/replay message cũ, không xóa queue đang chứa dữ liệu. Không đánh dấu Outbox/Inbox dùng chung đã hoàn thành chỉ vì có `EventEnvelope`.

## 3. Event v1 bắt buộc và consumer duy nhất

Các field trong cột payload là **required**, trừ field có dấu `?`; payload không chứa dữ liệu ngoài JSON Schema tương ứng. `correlationId/actorId/causationId` ở envelope. Tên queue là hợp đồng; chỉ tạo queue khi handler đã sẵn sàng.

| Event; aggregate / producer / trigger | Payload v1 | Queue → consumer và DB được sửa |
|---|---|---|
| `store.status-changed.v1`; `Store` / Store / admin approve, reject, lock, unlock | `storeId`, `ownerId`, `status`, `commissionRate`, `version`, `changedAt` | `identity.store-status` → Identity cập nhật `users.role` và `token_version` **khi APPROVED**; Inbox. REJECTED/LOCKED không tự khóa user. Catalog dùng Store HTTP ở V1, chưa có queue status |
| `catalog.batch-cancelled.v1`; `RescueBatch` / Catalog / `POST /api/store/batches/{id}/cancel` | `batchId`, `storeId`, `reason`, `cancelledAt` | `order.batch-cancelled` → Order hủy các order còn hiệu lực chứa batch, gọi release HTTP, phát refund request nếu đã PAID |
| `stock.reserved.v1`; `StockReservation` / Catalog / giữ kho HTTP | `reservationId`, `orderId`, `storeId`, `expiresAt`, `items[{batchId,quantity,unitPrice}]` | **Không có consumer nghiệp vụ V1**. HTTP reservation là kết quả cho Order; event đã có producer |
| `stock.released.v1`; `StockReservation` / Catalog / release HTTP | `reservationId`, `orderId`, `reason`; `releasedAt?` | **Không có consumer nghiệp vụ V1**. Order không gọi release lần nữa khi thấy event; producer đã có |
| `order.state-changed.v1`; `Order` / Order / tạo, payment, hủy, hết hạn, giao, refund status | `orderId`, `storeId`, `customerId`, `status`, `paymentStatus`, `totalAmount`, `commissionAmount`, `storeEarning`, `completedAt` nullable, `reservationId` nullable, `items[{batchId,quantity}]` | `finance.order-snapshots` → Finance UPSERT `order_snapshots` nếu version mới hơn; không tạo refund từ snapshot |
| `payment.succeeded.v1`; `Payment` / Finance / IPN hợp lệ SUCCESS | `paymentId`, `orderId`, `amount`, `txnRef`, `gatewayTxnNo`, `paidAt` | `order.payment-results` → Order xác nhận đơn nếu PENDING_PAYMENT; nếu đã CANCELLED/EXPIRED tạo refund request, không hồi sinh đơn |
| `payment.failed.v1`; `Payment` / Finance / IPN xác nhận FAILED cuối cùng | `paymentId`, `orderId`, `txnRef`, `responseCode`, `failedAt` | `order.payment-results` → Order cho thử lại trong hạn hoặc EXPIRED và release kho |
| `order.refund-required.v1`; `Order` / Order / hủy đã PAID hoặc payment muộn | `orderId`, `amount`, `reason`, `cancelledAt` nullable | `finance.refund-required` → kiểm tra `payments.SUCCESS`, `refunds.order_id UNIQUE`, tạo refund PENDING và Outbox `refund.requested.v1` |
| `refund.requested.v1`; `Refund` / Finance / tạo refunds.PENDING | `refundId`, `orderId`, `amount`, `reason`, `requestedAt` | `order.refund-status` → Order đặt `payment_status=REFUND_PENDING`, phát state snapshot |
| `refund.completed.v1`; `Refund` / Finance / admin xác nhận hoàn | `refundId`, `orderId`, `amount`, `gatewayRef`, `processedAt` | `order.refund-status` → Order đặt `payment_status=REFUNDED`, phát state snapshot |
| `audit.action.v1`; `AuditAction` / Store, Catalog, Order, Finance / thao tác cần audit | `action`, `entityType`, `entityId`; `amount?`, `detail?` | `identity.audit-events` → Identity ghi audit_logs + Inbox; consumer này đã có. Identity tự ghi audit của mình, không tự phát event |

Không phát `store.profile-changed.v1`, `catalog.batch-changed.v1`, `payout.state-changed.v1` trong **luồng bắt buộc V1** vì chưa có consumer/projection có tác dụng rõ. Khi có search projection hoặc thông báo payout, lập schema + queue + migration rồi thêm sau. Không để Finance/Store consume `order.state-changed.v1` chỉ để kiểm tra review; Store gọi Order HTTP.

JSON Schema máy đọc cho toàn bộ event bắt buộc nằm ở `contracts/schemas/`. Các schema `stock-reserved`, `order-state-changed`, `payment-succeeded` có ví dụ sẵn trong `contracts/examples/`. Consumer kiểm tra `eventType` và payload theo schema tương ứng. `aggregateType` của `audit.action.v1` do service nguồn chọn để tham chiếu thao tác; `eventType` vẫn là `audit.action.v1`.

## 4. State machine đã chốt

| Aggregate | Chuyển trạng thái hợp lệ | Quy tắc biên |
|---|---|---|
| Store | `PENDING → APPROVED/REJECTED`; `APPROVED ↔ LOCKED` | Chỉ APPROVED được đăng/giữ túi và nhận đơn mới; LOCKED không tự hủy đơn đã trả tiền |
| Batch | `AVAILABLE → SOLD_OUT/EXPIRED/CANCELLED`; `SOLD_OUT → AVAILABLE` khi được trả tồn trước pickupEnd | CANCELLED/EXPIRED là terminal cho đơn mới; hủy batch phát event cho Order |
| Reservation | `HELD → COMMITTED/RELEASED/EXPIRED`; `COMMITTED → RELEASED` chỉ khi hủy trước giao | Giữ/trả idempotent; mỗi order một reservation; Order chịu trách nhiệm gọi release HTTP |
| Order | `PENDING_PAYMENT → CONFIRMED/CANCELLED/EXPIRED`; `CONFIRMED → COMPLETED/NO_SHOW/CANCELLED` | `COMPLETED`, `NO_SHOW`, `CANCELLED`, `EXPIRED` terminal. Payment đến sau CANCELLED/EXPIRED **luôn hoàn tiền**, không thử giữ kho lại. `CREATING` chỉ là trạng thái ứng dụng, không lưu `orders` |
| Order paymentStatus | `UNPAID → PAID → REFUND_PENDING → REFUNDED` | Chỉ Finance IPN SUCCESS dẫn đến PAID; refund status theo event Finance. Đơn EXPIRED chưa trả tiền giữ UNPAID |
| Payment | `PENDING → SUCCESS/FAILED` | IPN lặp giữ nguyên kết quả; một `txn_ref` duy nhất. Muốn tạo attempt mới cần quy tắc/idempotency schema mới |
| Refund | `PENDING → COMPLETED` | V1 hoàn **toàn bộ một lần/đơn** theo `refunds.order_id UNIQUE`; gatewayRef + processedAt bắt buộc khi COMPLETED |
| Payout | `PENDING → PAID/CANCELLED` | PAID cần transferRef + paidAt; đơn đã payout không đưa vào kỳ khác |

Scheduler hết hạn và payment consumer dùng cập nhật có điều kiện trên cùng order row; chỉ một nhánh thắng. `COMPLETED`/`NO_SHOW` không bị batch cancel đảo ngược. Finance chỉ đưa order `COMPLETED/NO_SHOW` có payment SUCCESS, không refund, vào payout. `order.state-changed.v1` là full snapshot, Finance chỉ ghi field có trong `order_snapshots` V1.

## 5. Mã lỗi HTTP v1 cho frontend và service caller

Body chung `{code,message,details,timestamp,path}`. Không dùng câu tiếng Việt làm khóa xử lý. HTTP status phải khớp loại lỗi; 502/503 cho dependency không sẵn sàng. Các mã dưới là namespace chốt cho endpoint **sẽ triển khai**, không khẳng định code hiện đã trả đủ mã này.

| Miền | Mã lỗi | HTTP | Tình huống |
|---|---|---:|---|
| Chung | `VALIDATION_ERROR`, `UNAUTHENTICATED`, `FORBIDDEN`, `NOT_FOUND`, `IDEMPOTENCY_CONFLICT`, `DEPENDENCY_UNAVAILABLE` | 400, 401, 403, 404, 409, 503 | Lỗi request/quyền/key lặp khác body/service khác lỗi |
| Identity | `EMAIL_EXISTS`, `INVALID_CREDENTIALS`, `ACCOUNT_LOCKED`, `PASSWORD_CHANGE_REQUIRED` | 409, 401, 403, 403 | Giữ mã đã có nếu API hiện dùng; khi đổi phải cập nhật FE |
| Store | `STORE_ALREADY_REGISTERED`, `STORE_NOT_APPROVED`, `STORE_STATUS_CONFLICT`, `REVIEW_NOT_ALLOWED`, `REVIEW_ALREADY_EXISTS` | 409, 403, 409, 403, 409 | Duyệt/đánh giá/hồ sơ |
| Catalog | `BATCH_NOT_AVAILABLE`, `STOCK_NOT_ENOUGH`, `RESERVATION_CONFLICT`, `RESERVATION_NOT_FOUND` | 409, 409, 409, 404 | Giữ/trả tồn và checkout |
| Order | `CART_EMPTY`, `MULTI_STORE_CHECKOUT`, `ORDER_STATUS_CONFLICT`, `PICKUP_CODE_INVALID`, `PAYMENT_AMOUNT_MISMATCH` | 422, 422, 409, 404, 409 | Giỏ/đặt/hủy/giao và payment event |
| Finance | `PAYMENT_INTENT_INVALID`, `PAYMENT_ALREADY_FINAL`, `VNPAY_SIGNATURE_INVALID`, `REFUND_ALREADY_EXISTS`, `PAYOUT_ORDER_ALREADY_USED` | 409, 409, 400, 409, 409 | Tạo link/IPN/refund/payout |

`InternalServiceClient` hiện chỉ bọc HTTP status và response body; service caller phải map sang mã lỗi hợp đồng. `X-Correlation-Id` đi xuyên HTTP và event. Không trả stack trace, SQL, secret hoặc số tài khoản đầy đủ.

## 6. Migration/code bắt buộc trước khi mở luồng message

| Owner | Việc cần làm | Lý do dựa trên DB/code hiện tại |
|---|---|---|
| A Identity | Consumer `identity.store-status` + Inbox; role update tăng `users.token_version` | V2 đã có token_version, consumer chưa có |
| A Store | Producer status/audit; nếu phát status theo version thì thêm cột `stores.aggregate_version` V2 | Store V1 chưa có version |
| B Catalog | API commit reservation, chính sách release COMMITTED; thêm `rescue_batches.aggregate_version` khi phát batch cancel; tăng version reservation HELD=1, COMMITTED=2, RELEASED=2 hoặc 3 tùy đường chuyển; kiểm tra Store APPROVED bằng HTTP | `stock_reservations` có COMMITTED nhưng API hiện chỉ HELD→RELEASED và ghi release version cứng là 2; batch chưa có cột version |
| C Order | `orders.reservation_id UUID UNIQUE`, `aggregate_version BIGINT`, idempotency key/table, state transition và 3 consumer queue | V1 chưa có reservation/version/idempotency; `order.state-changed` cần version thực |
| D Finance | Idempotency/attempt khi tạo URL, `payments.aggregate_version`, `refunds.aggregate_version`, hai consumer queue, payment/refund producer; reconciliation job | `payments.txn_ref UNIQUE` chưa đủ cho retry cùng request; payment/refund V1 chưa có cột version; `refunds.order_id UNIQUE` đã hỗ trợ một refund |
| Cả nhóm | Sửa publisher MySQL/PostgreSQL, confirm/retry và DLX; thêm contract test/DB+Rabbit integration test | Publisher hiện dùng SQL UUID PostgreSQL cả trong MySQL, giữ transaction khi publish, chưa có retry/dead-letter thống nhất |

**Thứ tự bắt buộc:** tạo migration + consumer queue/binding + DLQ → triển khai consumer và kiểm thử lặp/đảo thứ tự → bật producer → nối Gateway/FE. `building-blocks` chỉ nhận phần cơ chế đã qua integration test trên **cả MySQL và PostgreSQL**; không đưa SQL, entity, DTO nghiệp vụ hoặc state machine vào module chung. Không xóa publisher riêng trước khi cơ chế chung thay thế và test chạy qua.

## 7. API kỹ thuật chung sẽ đưa vào `building-blocks`

Chốt **hợp đồng Java**, chưa đánh dấu runtime đã có. Phần này triển khai sau integration test tối thiểu ở mục dưới:

```java
public interface OutboxStore {
    void append(EventEnvelope<?> event);       // Cùng transaction với business row.
    List<PendingEvent> claim(int limit);         // Claim an toàn giữa nhiều publisher instance.
    void markPublished(UUID eventId);
    void scheduleRetry(UUID eventId, int attempts, Instant nextAttemptAt, String safeError);
}

public interface InboxStore {
    boolean tryBegin(String consumerName, UUID eventId); // Unique key (consumer_name,event_id).
}

public interface EventHandler<T> {
    void handle(EventEnvelope<T> event);        // Nghiệp vụ do service nhận triển khai.
}
```

`building-blocks` sở hữu `EventEnvelope`, validator/serializer, vòng publish confirm/retry và wrapper consume transaction/ACK. **Mỗi service** sở hữu adapter `OutboxStore`/`InboxStore` cho DB của mình, queue/binding, DTO payload và `EventHandler`. Không đưa `JdbcTemplate` SQL chung vào module vì MySQL dùng `CHAR(36)`/JSON/DATETIME còn PostgreSQL dùng UUID/JSONB/TIMESTAMPTZ; cú pháp `CAST(? AS UUID)` hiện chỉ hợp PostgreSQL. Các method `claim/markPublished/scheduleRetry` phải có thiết kế lease/claim để không giữ DB transaction mở khi chờ Rabbit confirm; nếu publisher crash sau confirm nhưng trước mark, event được phát lại cùng eventId và consumer Inbox xử lý lặp an toàn.

**Integration test bắt buộc trước khi chuyển publisher hiện tại sang cơ chế chung:** chạy với MySQL và PostgreSQL thật (Testcontainers hoặc Compose), RabbitMQ thật; xác nhận rollback business transaction không còn Outbox, commit tạo đúng envelope, publisher confirm mới mark, broker lỗi ghi retry, hai publisher không claim cùng row, redelivery chỉ tạo một Inbox/business effect, event sai schema vào DLQ và replay không tạo refund/stock movement trùng. Test này là cổng nghiệm thu của building-blocks, không cần viết test chỉ để so sánh getter/record.

## 8. Quyền sở hữu DB và quy ước HTTP

| Service | DB và bảng sở hữu | API nội bộ cung cấp |
|---|---|---|
| Identity | MySQL `identity_db`: `users`, `audit_logs`, Outbox/Inbox | `GET /internal/users/{id}/status`, `GET /internal/users/count`, tạo/khóa staff |
| Store | MySQL `store_db`: `stores`, `reviews`, Outbox/Inbox | `GET /internal/stores/{id}/checkout-policy`, payout account, store count |
| Catalog | PostgreSQL `catalog_db`: `categories`, `rescue_batches`, `stock_movements`, `stock_reservations`, `stock_reservation_items`, Outbox/Inbox | `POST/GET /internal/reservations`, release/commit reservation |
| Order | PostgreSQL `order_db`: `cart_items`, `orders`, `order_items`, Outbox/Inbox | Payment intent, review eligibility |
| Finance | PostgreSQL `finance_db`: `payments`, `refunds`, `payouts`, `payout_items`, `order_snapshots`, Outbox/Inbox | Payment status để đối soát nếu cần |

ID thuộc service khác chỉ là tham chiếu, **không có FK xuyên DB**. Chỉ owner được ghi bảng của mình. `users.store_id` là liên kết staff do Identity ghi; `stores.owner_id` do Store ghi. Catalog sở hữu `remaining_quantity`; Order sở hữu `orders.status/payment_status`; Finance sở hữu giao dịch và snapshot tài chính. `order_snapshots` là projection, không là nguồn thay đổi đơn.

Browser chỉ gọi `/api/**` qua Gateway; service gọi `/internal/**` trực tiếp. JSON dùng `camelCase`, ID số nguyên 64 bit, tiền VND integer, thời gian API ISO-8601 có offset (identity hiện còn `LocalDateTime` không offset, cần chuyển có kiểm soát). `Authorization: Bearer <JWT>` cho route bảo vệ; `X-Correlation-Id` xuyên request/event; `Idempotency-Key` cho tạo đơn, link thanh toán và payout; `X-Internal-Key` chỉ ở service-to-service local. Không tin userId/storeId/role browser gửi trong body. Internal client có timeout, chỉ tự retry GET hoặc POST idempotent. Error body và mã lỗi ở mục 5; frontend không dựa vào chuỗi message để quyết định hành vi.

## 9. Mẫu code và file hợp đồng

Một publisher tạo `EventEnvelope<T>` rồi ghi **toàn envelope** vào Outbox trong transaction của use case:

```java
record StoreStatusChanged(long storeId, long ownerId, String status,
                          BigDecimal commissionRate, long version, Instant changedAt) {}

var event = EventEnvelope.create(
    "store.status-changed.v1", "Store", Long.toString(storeId), version,
    correlationId, null, adminUserId,
    new StoreStatusChanged(storeId, ownerId, "APPROVED", rate, version, Instant.now())
);
// UPDATE stores ...; INSERT outbox_events(..., serializedWholeEnvelope, ...)
```

`EventEnvelope.create` tạo metadata, **không** tự ghi DB/gửi RabbitMQ. Adapter persistence của từng service ghi Outbox; publisher dùng cơ chế chung mục 7 để gửi sau commit. Consumer parse/schema validate → Inbox dedup → đổi DB của mình → commit → ACK. Ví dụ `FinanceRefundRequiredConsumer` tạo `refunds.PENDING` và Outbox `refund.requested.v1` trong cùng transaction; `causationId` của event mới là `eventId` nhận được. Không mở DB transaction trong khi gọi HTTP service khác.

`contracts/schemas/*.schema.json` và `contracts/examples/*.json` là hợp đồng máy đọc. Mỗi event bắt buộc ở mục 3 đã có schema và ví dụ; khi đổi field phải cập nhật cả hai và chạy `python contracts/validate.py` (cài thư viện từ `contracts/requirements.txt`). Không đưa DTO business, entity JPA, SQL migration hay controller của một service vào `building-blocks`.
