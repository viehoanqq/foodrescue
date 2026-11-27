# Payment & Finance — phần việc người D

Service Java 17/Spring Boot, Spring Data JPA/Hibernate, PostgreSQL `finance_db`, cổng `8086`. Hợp đồng tích hợp: [`contracts/quyet-dinh-tich-hop-v1.md`](../../contracts/quyet-dinh-tich-hop-v1.md). Swagger: `http://localhost:8086/swagger-ui.html`.

## Truy cập dữ liệu bằng JPA

- `JpaFinanceLedger` sử dụng `PaymentRepository`, `RefundRepository`, `PayoutRepository`, `PayoutItemRepository` cho CRUD nghiệp vụ. Entity chứa đầy đủ các cột idempotency và aggregate version của V2.
- Consumer snapshot sử dụng `OrderSnapshotRepository`, chỉ ghi khi version mới hơn; khóa theo order giữ an toàn khi snapshot đầu tiên chưa tồn tại.
- Xác nhận refund/payout dùng `PESSIMISTIC_WRITE`. Các transaction nghiệp vụ dùng Spring `@Transactional` với `JpaTransactionManager`.
- `JpaFinanceReports` và `JpaFinanceQueries` dùng native query qua `EntityManager` cho báo cáo tổng hợp, PostgreSQL advisory lock, Inbox dedup và Outbox claim/retry. Mã production không dùng `JdbcTemplate`; test dùng JDBC để tạo/kiểm tra dữ liệu độc lập với JPA.
- Flyway quản lý schema; Hibernate dùng `ddl-auto: validate`, không tự sửa schema.

## Chức năng

- Tạo URL VNPay đồng bộ từ payment intent của Order; không tin số tiền/customerId do trình duyệt gửi. Một attempt PENDING/SUCCESS mỗi đơn. Cùng customer và `Idempotency-Key` trả URL đã lưu; key khác chỉ tạo attempt sau khi attempt trước FAILED. Key đã dùng cho đơn khác trả 409. PENDING hết hạn chưa có IPN vẫn được giữ để tránh thu tiền hai lần; cần đối soát merchant trước khi xử lý giao dịch treo.
- IPN xác minh HMAC-SHA512, merchant, mã giao dịch, số tiền và cả hai mã kết quả. IPN lặp trả `RspCode=02`; sai chữ ký `97`, không tìm thấy `01`, sai tiền `04`. Return chỉ đọc trạng thái đã ghi, không cập nhật payment.
- Consumer snapshot UPSERT theo version; consumer refund ghi Inbox, yêu cầu hoàn bền và Outbox trong transaction. Yêu cầu hoàn đến trước IPN được giữ lại; job đối soát mỗi 30 giây xử lý payment SUCCESS có đơn CANCELLED/EXPIRED nhưng chưa có refund.
- Admin xác nhận hoàn thủ công bằng mã giao dịch. Tạo/chi/hủy payout có snapshot ngân hàng, khóa cạnh tranh và unique active payout item. Chỉ chọn COMPLETED/NO_SHOW có payment SUCCESS đúng tiền, không có refund/yêu cầu refund. Payout PAID không thể hủy.
- Doanh thu theo ngày, sao kê, danh sách giao dịch, dashboard và công thức đối soát. Doanh thu tính theo ngày chốt đơn; đối soát là số lũy kế, có thể tạm chênh lệch khi event chưa tới. Dashboard hiện trả tổng/biểu đồ đơn đã chốt và count Identity/Store; chưa có số túi vì projection chỉ giữ header.
- Outbox PostgreSQL claim có lease, publish persistent JSON, đợi confirm và kiểm tra returned message trước khi đánh dấu. Lỗi được retry với backoff tối đa 300 giây. Consumer retry 4 lần rồi DLQ.

## Chạy và cấu hình

Từ repository root:

```sh
mvn -B -f services/pom.xml -pl payment-finance-service -am verify
cp .env.example .env # chỉ khi chưa có .env
# Điền cấu hình trong .env trước khi chạy:
docker compose --env-file .env --profile services --profile gateway up -d --build
```

Các biến: `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_RETURN_URL` (mặc định `http://localhost:5173/payment/result`), `JWT_SECRET`, `INTERNAL_API_KEY`, `FINANCE_STORE_API_KEY`. Finance dùng khóa riêng để đọc payout account; không dùng khóa nội bộ thường cho endpoint này. Compose đã truyền các cấu hình cần thiết. Chạy ngoài Docker phải đặt thêm `DB_URL/DB_USERNAME/DB_PASSWORD`, `RABBITMQ_HOST/PORT/USER/PASSWORD`, `ORDER_SERVICE_URL`, `STORE_SERVICE_URL`, `IDENTITY_SERVICE_URL`.

Đăng ký IPN HTTPS có thể được VNPay truy cập tại `/api/payments/vnpay/ipn` qua Gateway. Không dùng Return làm thay thế IPN khi chạy local. Không có merchant credentials thì tạo URL trả 503. Hướng dẫn chính thức: https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html.

Flyway V2 giữ nguyên V1, thêm idempotency/version, yêu cầu refund và lease. Nếu DB cũ có nhiều payment PENDING/SUCCESS cùng order, cần đối soát các bản ghi đó trước khi áp unique index; migration không tự xóa dữ liệu tiền.

## API bàn giao

API public qua Gateway `http://localhost:8080`; `/internal/**` gọi trực tiếp service. Bearer token được xác minh và kiểm tra trạng thái hiện hành với Identity. Chủ cửa hàng được xác định qua Store profile bằng token, không lấy `storeId` của chủ từ query/body.

| Quyền | Endpoint | Đầu vào |
|---|---|---|
| CUSTOMER | `POST /api/payments/vnpay-url` | `{ "orderId": 1 }`, `Idempotency-Key` |
| Public | `GET /api/payments/vnpay/ipn` | Tham số VNPay có chữ ký |
| Public | `GET /api/payments/vnpay/return` | Tham số VNPay có chữ ký; chỉ đọc |
| ADMIN | `GET /api/admin/refunds`, `/{id}` | Danh sách có `status`, `page`, `size`; chi tiết có giao dịch VNPay gốc |
| ADMIN | `POST /api/admin/refunds/{id}/complete` | `{ "gatewayRef": "REFUND-001" }` |
| ADMIN | `GET /api/admin/payouts`, `/{id}` | Lọc `storeId`, `status`, `page`, `size` |
| ADMIN | `POST /api/admin/payouts` | `{ "storeId": 1, "from": "2026-11-01", "to": "2026-11-27" }`, `Idempotency-Key` |
| ADMIN | `POST /api/admin/payouts/{id}/paid` | `{ "transferRef": "BANK-001" }` |
| ADMIN | `POST /api/admin/payouts/{id}/cancel` | Không body |
| STORE_OWNER | `GET /api/store/revenue`, `/api/store/statement` | `from`, `to`; statement thêm `page`, `size` |
| STORE_OWNER | `GET /api/store/payouts`, `/{id}` | Danh sách có `status`, `page`, `size` |
| ADMIN | `GET /api/admin/finance/reconciliation` | Số lũy kế, các khoản và `difference` |
| ADMIN | `GET /api/admin/finance/payments` | `status`, `page`, `size` |
| ADMIN | `GET /api/admin/dashboard` | `from`, `to` |
| Internal | `GET /internal/payments/orders/{orderId}/status` | `X-Internal-Key` |

Danh sách trả `{items,total,page,size}`, page từ 0, size 1–100. Ngày `from/to` gồm cả hai đầu. Số tài khoản trong response payout được che, bản đầy đủ chỉ lưu trong DB Finance. API ghi tiền phát audit; không có API tự tạo refund từ dashboard.

## Message và tích hợp

- `finance.order-snapshots` nhận `order.state-changed.v1`.
- `finance.refund-required` nhận `order.refund-required.v1`.
- DLQ: `<queue>.dead`, direct exchange `foodrescue.dlx`. Kiểm tra DLQ và sửa nguyên nhân trước khi replay; giữ nguyên `eventId` khi replay.
- Phát `payment.succeeded/failed.v1`, `refund.requested/completed.v1`, `audit.action.v1`. Người C cần bật `order.payment-results` và `order.refund-status`; Identity nhận audit. Không chuyển SQL của Finance sang building-blocks.
- Người A cung cấp payout account/count và Store profile. Người C cung cấp payment intent, snapshot có version và refund-required. Không truy cập DB của A/C.
- Frontend nghiệp vụ hiện vẫn có luồng demo; cần dùng API phía trên để thay thao tác thanh toán/doanh thu/refund/payout. Trạng thái SUCCESS ở payment chỉ xuất hiện sau IPN; Order cập nhật bất đồng bộ. Phần này triển khai backend của D, không thay luồng giỏ/đặt đơn của C.

## Kiểm thử

Unit test chạy với Maven thông thường. Integration test dùng PostgreSQL và RabbitMQ thật, chỉ bật khi có biến môi trường:

```sh
docker run -d --rm --name foodrescue-finance-test-db \
  -e POSTGRES_PASSWORD=finance_test -e POSTGRES_DB=finance_test \
  -p 127.0.0.1:55439:5432 postgres:17
docker run -d --rm --name foodrescue-finance-test-rabbit \
  -p 127.0.0.1:56739:5672 rabbitmq:4-management
# Chờ PostgreSQL/RabbitMQ sẵn sàng rồi chạy:
FINANCE_TEST_DB_URL=jdbc:postgresql://localhost:55439/finance_test \
FINANCE_TEST_RABBIT_PORT=56739 \
mvn -B -f services/pom.xml -pl payment-finance-service -am verify
docker stop foodrescue-finance-test-db foodrescue-finance-test-rabbit
```

Các test xóa dữ liệu trong DB kiểm thử `finance_test`; không dùng DB nghiệp vụ. Bao phủ quyền sở hữu đơn, signature/amount, retry link, IPN lặp, Return không ghi tiền, rollback, event đảo thứ tự, refund trước IPN, reconciliation, payout cạnh tranh, confirm/retry và claim publisher đồng thời. Import `postman/finance.postman_collection.json` để thử API với token của từng vai trò. Chưa thay thế kiểm thử merchant sandbox có IPN công khai và end-to-end với các service khác.
