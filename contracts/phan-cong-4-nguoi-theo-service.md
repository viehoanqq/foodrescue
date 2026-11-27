# Phân công 4 người theo 5 service

> Hướng dẫn làm việc theo service. Quy định chung duy nhất: [quyet-dinh-tich-hop-v1.md](quyet-dinh-tich-hop-v1.md); kiến trúc tổng quan: [docs/KienTruc_Microservices_Outbox_RabbitMQ.md](../docs/KienTruc_Microservices_Outbox_RabbitMQ.md). **Đã có** khác **phải làm**. Frontend lưu dữ liệu nghiệp vụ trong `localStorage`; các mục dưới đây là việc thay demo bằng API thật.

## Quy tắc chung trước khi chia nhánh

- Mỗi người sửa code, Flyway migration và publisher/consumer trong **service mình sở hữu**. Không ghi DB của service khác. Controller ở `api/`, use case/port ở `application/`, quy tắc thuần ở `domain/`, DB/HTTP/Rabbit ở `infrastructure/`.
- Các API công khai đi qua Gateway khi dùng được; `/internal/**` chỉ dùng service-to-service. API nội bộ cần `X-Internal-Key` ở local và timeout. FE không gửi userId/storeId tự nhận là thật.
- Mỗi event dùng mẫu ở [quy định chung](quyet-dinh-tich-hop-v1.md#9-mẫu-code-và-file-hợp-đồng): business row + Outbox cùng transaction; consumer Inbox + tác động DB cùng transaction. Mỗi queue là của **service nhận**, khai báo binding trước khi bật producer. Không tạo queue chỉ để nhận rồi bỏ qua.
- Cả nhóm review `contracts/` trước khi đổi payload, status, URL hoặc error code. Owner producer và owner consumer cùng duyệt schema/examples. Cần migration mới, không sửa migration V1/V2 đã áp dụng.
- Gateway/Compose/building-blocks là việc chung: người A giữ cấu hình tích hợp, nhưng PR của service nào thêm route/binding thì người đó cập nhật và yêu cầu người nhận review. Frontend route/API client của tính năng do owner service đó nối khi API hoàn thành.

## Người A — `identity-service` và `store-service`

### A1. Identity: tài khoản, quyền và audit

**DB sở hữu:** MySQL `identity_db`: `users`, `audit_logs`, `outbox_events`, `inbox_events`. `users.store_id` dành cho nhân viên; chủ cửa hàng liên kết qua `stores.owner_id` của Store. Không tạo user mới khi khách gửi hồ sơ mở cửa hàng.

**Đã có:** đăng ký/đăng nhập JWT, `/api/me`, đổi mật khẩu, admin user lock/unlock/reset, danh sách audit có lọc; `GET /internal/users/{id}/status`, `GET /internal/users/count`, `POST /internal/staff`, `POST /internal/staff/{id}/lock` và `/unlock`; audit thao tác identity ghi cùng transaction; listener `audit.action.v1` với Inbox và DLQ. Seed admin/owner local. Cần giữ nguyên hành vi này khi thêm event.

**API Identity đã có; việc nối Store/FE còn lại:**

| API | Việc cần làm |
|---|---|
| `POST /internal/staff/{id}/lock`, `/unlock` | Đã có ở Identity; Store cần nối sau khi kiểm tra quyền chủ với cửa hàng tương ứng |
| `GET /api/admin/audit-logs` | Đã có lọc actor/action/entity/thời gian và phân trang; FE admin có thể nối bộ lọc |

**Consumer phải làm:** `identity.store-status` bind `store.status-changed.v1` từ Store. Khi `APPROVED`, kiểm tra `ownerId` tồn tại và hợp lệ, đổi **user hiện có** sang `STORE_OWNER`, tăng `token_version`, ghi Inbox; user đăng nhập lại để nhận role mới. `REJECTED` không cấp quyền. Cần chốt chính sách LOCKED/UNLOCKED của store trước khi tự đổi quyền user. `identity.audit-events` nhận `audit.action.v1` từ bốn service đã có; bổ sung kiểm tra envelope/schema, retry hữu hạn và quan sát DLQ theo mẫu chung.

**Publisher:** thao tác chính identity ghi audit trực tiếp, **không phát `audit.action.v1` để tự nhận lại**. Chỉ thêm identity business event khi đã có consumer và tác dụng rõ. Bảng Outbox hiện có không có nghĩa identity bắt buộc phải phát event.

### A2. Store: hồ sơ, duyệt, nhân viên, đánh giá

**DB sở hữu:** MySQL `store_db`: `stores`, `reviews`, Outbox/Inbox. `stores.owner_id UNIQUE`, `reviews.order_id UNIQUE`. Store không ghi `users` hoặc `orders`.

**Cấu trúc code:** `StoreController` xử lý đăng ký/hồ sơ/cửa hàng công khai/đánh giá; `StoreStaffController` xử lý nhân viên; `AdminStoreController` xử lý duyệt và quản trị. Admin có use case, port và JDBC repository riêng (`ReadAdminStoreUseCase`, `AdminStoreUseCase`, `IAdminStoreRepository`, `JdbcAdminStoreRepository`). Khi triển khai `/internal/stores/**`, đặt vào `InternalStoreController` riêng.

**API phải làm:**

| Nhóm | Endpoint/luồng |
|---|---|
| Đăng ký | `POST/GET /api/store/registration`: CUSTOMER đã đăng nhập gửi hồ sơ, giấy phép; ghi `stores.PENDING`, trả trạng thái. Admin xem tại Store; **không cần message “gửi duyệt”** |
| Cửa hàng công khai | `GET /api/stores`, `GET /api/stores/{id}`, `GET /api/stores/{id}/reviews`: chỉ APPROVED hiển thị công khai, có phân trang |
| Hồ sơ chủ | `GET/PUT /api/store/profile`: sửa thông tin hợp lệ, không tự sửa `owner_id`, giới hạn thông tin ngân hàng trả ra |
| Nhân viên | `GET/POST /api/store/staff`, khóa/mở staff: kiểm tra quyền chủ rồi gọi lệnh nội bộ Identity; store không tạo user trực tiếp trong DB mình |
| Đánh giá | `POST /api/stores/{id}/reviews`: gọi `GET /internal/orders/{id}/review-eligibility`; chỉ khách có đơn COMPLETED, một review/đơn |
| Admin Store | `GET /api/admin/stores`, `POST .../{id}/approve`, `/reject`, `/lock`, `/unlock`, `PUT .../{id}/commission`; lưu actor, lý do, thời điểm |
| Nội bộ | `GET /internal/stores/{id}/checkout-policy` cho Order/Catalog; `GET /internal/stores/{id}/payout-account` cho Finance; `GET /internal/stores/count` cho dashboard |

**Publisher phải làm:** trong transaction đổi `stores.status`, ghi `store.status-changed.v1` và `audit.action.v1` (nếu do admin thao tác). `store.status-changed.v1` tới `identity.store-status`; `catalog.store-status` **chỉ** mở sau khi B có projection và migration, nếu chưa thì B gọi HTTP policy. Sửa vị trí/hoa hồng có thể phát `store.profile-changed.v1` **chỉ khi** Catalog có projection search nhận; Order luôn lấy commission policy đồng bộ lúc checkout. Đăng ký PENDING không phát event để admin duyệt.

**Consumer:** chưa có consumer nghiệp vụ Store bắt buộc ở V1. Payout/statement đọc Finance qua Gateway; review kiểm tra Order bằng HTTP, không nhận toàn bộ `order.state-changed.v1`.

**Bàn giao:** A cung cấp Store policy cho B/C, payout account/count cho D, event status cho Identity. FE: thay form đăng ký demo, hồ sơ, staff, review và duyệt store bằng API thật.

## Người B — `catalog-inventory-service`

**DB sở hữu:** PostgreSQL `catalog_db`: `categories`, `rescue_batches`, `stock_movements`, `stock_reservations`, `stock_reservation_items`, Outbox/Inbox. Chỉ Catalog được sửa `remaining_quantity`; `stock_movements` chỉ thêm. `order_id`, `store_id` là tham chiếu, không FK xuyên DB.

**Đã có:** `POST/GET /internal/reservations`, `POST /internal/reservations/{id}/release`; giữ/trả nhiều túi, khóa row và trừ kho atomic; `stock.reserved.v1`/`stock.released.v1` vào Outbox. Cần thêm kiểm tra store APPROVED trước khi dùng cho đơn thật.

**API phải làm:**

| Nhóm | Endpoint/luồng |
|---|---|
| Danh mục | `GET /api/categories`; admin `POST/PUT/DELETE /api/admin/categories/**`; không xóa category còn batch tham chiếu |
| Khám phá | `GET /api/batches` theo category, giá, tọa độ/bán kính, thời gian; `GET /api/batches/{id}`. Chỉ AVAILABLE, còn hàng, còn giờ và store APPROVED; OSRM dùng cho quãng đường/ETA với timeout/fallback |
| Túi của cửa hàng | `GET/POST/PUT /api/store/batches/**`, `PATCH .../{id}/quantity`, `GET .../{id}/stock-movements`, `POST .../{id}/cancel`; giá cứu ≤60% giá gốc, thời gian nhận hợp lệ, điều chỉnh tồn có sổ kho |
| Reservation nội bộ | Hoàn thiện giữ/trả idempotent, `GET /internal/reservations/{id}` khi Order timeout; chốt và thêm commit reservation khi thanh toán nếu dùng trạng thái `COMMITTED` |
| Internal batch | `GET /internal/batches/{id}` nếu Order cần đối chiếu ngoài kết quả reservation |

**Publisher phải làm:** `catalog.batch-cancelled.v1` khi chủ/admin hủy túi → queue `order.batch-cancelled`, consumer Order hủy các đơn còn hiệu lực. Ghi `audit.action.v1` cùng transaction cho sửa tồn/hủy túi → `identity.audit-events`. `stock.reserved.v1`/`stock.released.v1` đã có producer; V1 **không bắt buộc Order consume** vì HTTP reservation là nguồn kết quả. `catalog.batch-changed.v1` chỉ thêm khi có projection/search thật cần nhận.

**Consumer:** mặc định V1 dùng HTTP `checkout-policy` từ Store khi tạo/giữ túi. Nếu nhóm chọn projection cho search, B tạo migration bảng store projection, queue `catalog.store-status` cho `store.status-changed.v1` và có thể `catalog.store-profile` cho `store.profile-changed.v1`; Inbox + version/freshness, không dùng event không có chỗ lưu bền. **Không consume `order.state-changed.v1` để trả kho**: C gọi release HTTP idempotent.

**Bàn giao:** B chốt response reservation gồm giá/khung giờ với C, event batch cancel với C; FE thay catalog/demo batch bằng API khi có auth và pagination.

## Người C — `order-service`

**DB sở hữu:** MySQL `order_db`: `cart_items`, `orders`, `order_items`, Outbox/Inbox. Mỗi `orders.store_id` là **một cửa hàng**; giỏ nhiều store phải tách đơn hoặc chặn rõ. Order chụp giá, commission, khung giờ; không sửa kho hay payment/refund DB.

**Đã có:** `InventoryPort` và adapter HTTP gọi Catalog reservation; `/internal/order/reservations/**` hiện là đường thử, chưa tạo đơn.

**API phải làm:**

| Nhóm | Endpoint/luồng |
|---|---|
| Giỏ | `GET/POST/PUT/DELETE /api/cart/**`; không giữ tồn khi chỉ thêm giỏ, giới hạn số lượng theo schema |
| Đặt đơn | `POST /api/orders`: kiểm tra Store APPROVED/commission bằng HTTP, gọi Catalog giữ kho, rồi ghi order/items + Outbox; `Idempotency-Key`, release bù nếu ghi Order thất bại; không giữ DB transaction khi gọi HTTP |
| Khách | `GET /api/orders`, `GET /api/orders/{id}`, `POST .../{id}/cancel`; chỉ chủ đơn xem/hủy theo state machine |
| Cửa hàng | `GET /api/store/orders/**`, `GET /api/store/pickup/lookup`, `POST /api/store/pickup/handover`; chỉ store phù hợp xử lý, mã nhận hàng chỉ mở sau payment SUCCESS |
| Admin | `GET /api/admin/orders/**`, lệnh hủy theo quyền |
| Nội bộ | `GET /internal/orders/{id}/payment-intent` cho Finance; `GET /internal/orders/{id}/review-eligibility` cho Store |
| Scheduler | Hết hạn thanh toán, NO_SHOW; dùng transition có điều kiện và release kho một lần |

**Migration phải làm trước:** thêm `reservation_id`, `aggregate_version`, idempotency key hoặc bảng request tương ứng vào order DB. Chốt một order ↔ một reservation. `order.state-changed.v1` phải có version tăng cho mỗi đổi trạng thái để Finance không ghi đè snapshot mới bằng event cũ.

**Publisher phải làm:** `order.state-changed.v1` full snapshot sau tạo/xác nhận/hủy/hết hạn/giao/NO_SHOW/thay đổi paymentStatus → `finance.order-snapshots`. Khi hủy đơn đã thu tiền hoặc payment về muộn cho đơn đã hủy, ghi `order.refund-required.v1` → `finance.refund-required`; Finance kiểm tra payment SUCCESS trước khi tạo refund. Thao tác admin/store hủy đơn ghi thêm `audit.action.v1` → Identity. **Không phát `order.payment-requested.v1` để lấy URL VNPay**; browser gọi Finance HTTP đồng bộ.

**Consumer phải làm:** `order.payment-results` bind `payment.succeeded.v1` và `payment.failed.v1` từ Finance; xác minh order/amount/state, chống callback lặp. `order.refund-status` bind `refund.requested.v1`, `refund.completed.v1` từ Finance; cập nhật `orders.payment_status`. `order.batch-cancelled` bind `catalog.batch-cancelled.v1` từ Catalog; tìm order_items, hủy các đơn còn hợp lệ, refund nếu đã thu tiền, release reservation qua HTTP. Mỗi consumer có Inbox và DLQ; không để `COMPLETED`/`NO_SHOW` tự bị hủy sai quy tắc. Stock event chưa cần queue Order ở V1.

**Bàn giao:** C cung cấp payment intent/review eligibility cho D/A, snapshot và refund request cho D, nhận batch cancel từ B; FE thay giỏ/đơn/QR/quầy giao demo bằng API.

## Người D — `payment-finance-service`

**DB sở hữu:** PostgreSQL `finance_db`: `payments`, `refunds`, `payouts`, `payout_items`, `order_snapshots`, Outbox/Inbox. `refunds.order_id UNIQUE` hiện cho **một refund toàn phần/đơn**; `payout_items` không cho một đơn vào hai kỳ chi trả đang hiệu lực.

**API phải làm:**

| Nhóm | Endpoint/luồng |
|---|---|
| VNPay | `POST /api/payments/vnpay-url`: gọi Order payment-intent, kiểm tra trạng thái/số tiền, tạo `payments.PENDING` idempotent và trả URL **đồng bộ**. VNPay IPN/return xác minh chữ ký, `txnRef`, amount; IPN quyết định trạng thái tiền, return chỉ hiển thị kết quả |
| Refund | `GET/POST /api/admin/refunds/**`: xem yêu cầu, xác nhận hoàn/ghi gatewayRef, chống hoàn lặp; không tự tạo refund từ dashboard |
| Doanh thu cửa hàng | `GET /api/store/revenue`, `/api/store/statement`, `/api/store/payouts/**`; chỉ trả cửa hàng của caller |
| Chi trả kỳ | `GET/POST /api/admin/payouts/**`, đánh dấu PAID/hủy; chọn đơn đủ điều kiện từ snapshot + payment/refund thật, chụp tài khoản ngân hàng qua Store HTTP, giữ unique payout item |
| Admin | `GET /api/admin/finance/**`, `GET /api/admin/dashboard?from&to`; dashboard lấy count nhẹ từ Identity/Store qua HTTP có timeout |
| Nội bộ | `GET /internal/payments/orders/{orderId}/status` nếu Order cần đối soát thanh toán muộn |

**Migration phải làm trước:** chốt idempotency key/attempt khi tạo link nhiều lần vì `payments` hiện chỉ unique `txn_ref`; nếu muốn hoàn một phần/nhiều lần thì phải đổi `refunds.order_id UNIQUE` và hợp đồng event. `order_snapshots` hiện chỉ giữ header, không giả định đã có item projection.

**Consumer phải làm:** `finance.order-snapshots` bind `order.state-changed.v1` từ Order → UPSERT `order_snapshots` khi `aggregateVersion` mới hơn. `finance.refund-required` bind `order.refund-required.v1` từ Order → kiểm tra `payments.SUCCESS`, `refunds.order_id UNIQUE`, tạo refund PENDING + `refund.requested.v1` trong cùng transaction. Cần job đối soát payment SUCCESS mà order hủy/expire chưa có refund để chống mất event hoặc race.

**Publisher phải làm:** VNPay IPN hợp lệ ghi `payment.succeeded.v1` hoặc thất bại cuối cùng ghi `payment.failed.v1` → `order.payment-results`; tạo refund PENDING ghi `refund.requested.v1` → `order.refund-status`; xác nhận hoàn ghi `refund.completed.v1` → `order.refund-status`. Thao tác hoàn/payout admin ghi `audit.action.v1` → Identity. `payout.state-changed.v1` **chưa bắt buộc** nếu không có consumer thông báo/projection; Store UI đọc Finance API trực tiếp.

**Bàn giao:** D chốt payload payment/refund với C, dùng snapshot C, lấy payout account/count từ A; FE thay thanh toán/doanh thu/refund/payout/dashboard demo bằng API thật.

## Thứ tự tích hợp để nhóm không chờ nhau

1. **Cả nhóm:** áp dụng [quy định chung](quyet-dinh-tich-hop-v1.md). Triển khai và kiểm thử Outbox/Inbox trên MySQL lẫn PostgreSQL trước khi đưa cơ chế chung vào `building-blocks`; từng service vẫn sở hữu DTO, adapter và SQL của mình.
2. **A + B:** Store policy và Catalog API batch/reservation. B chặn store chưa APPROVED trước giữ kho; A triển khai đăng ký/duyệt và Identity consumer cấp role.
3. **B + C:** đặt đơn thật dùng reservation HTTP, release idempotent, batch cancel event. C tạo `order.state-changed.v1` để D triển khai snapshot.
4. **C + D:** payment intent HTTP, VNPay IPN → payment result event, hủy/refund event, đối soát payment muộn.
5. **A + D:** account ngân hàng, doanh thu/payout, dashboard count. Sau mỗi API hoàn tất, người sở hữu cập nhật Gateway route và frontend; xóa đoạn demo tương ứng khi API đã chạy.

**Điều kiện bàn giao mỗi service:** migration chạy trên DB rỗng và DB cũ; API có auth/validation và response/error đúng hợp đồng; Outbox cùng transaction; consumer Inbox idempotent, retry hữu hạn/DLQ; test luồng chính và trường hợp lặp/đảo thứ tự; README và `contracts/` cập nhật. Không đánh dấu hoàn thành chỉ vì đã có entity hoặc publisher scheduler.

## Checklist làm từng bước theo service

Các bước trong mỗi hàng thực hiện theo thứ tự. Chỉ bật producer khi queue và consumer phía nhận đã sẵn sàng. Mỗi bước xong phải cập nhật trạng thái trong PR của owner.

| Owner/service | Bước 1: DB + domain | Bước 2: API/HTTP | Bước 3: message | Bước 4: tích hợp UI và kiểm tra |
|---|---|---|---|---|
| A / Identity | Chốt role/`token_version`; thêm logic idempotent đổi CUSTOMER → STORE_OWNER, tạo staff theo ràng buộc `users.store_id` | `GET /internal/users/count`, lệnh tạo/khóa staff; giữ auth/admin user hiện có | Tạo queue `identity.store-status`, handler Inbox; hoàn thiện audit DLQ/retry; test nhận lại APPROVED không tăng tokenVersion hai lần | Thử duyệt store → user đăng nhập lại có role chủ; admin user/audit qua Gateway |
| A / Store | Migration `stores.aggregate_version` nếu phát status; use case đăng ký PENDING, duyệt, khóa, đánh giá; owner_id và order_id unique | Public store/profile/staff/review, admin stores, internal policy/payout account/count | Ghi `store.status-changed.v1` + `audit.action.v1` cùng transaction; bật sau khi Identity queue sẵn | FE đăng ký/duyệt/hồ sơ/staff/review; test PENDING không hiện công khai và không checkout |
| B / Catalog | Migration batch version; hoàn thiện reservation commit/release, sổ kho append-only, concurrency và kiểm tra APPROVED | Category/batch/store batch APIs, OSRM, internal reservation/release/commit | `catalog.batch-cancelled.v1` + audit; giữ stock events đã có; không consume order state để trả kho | FE tìm túi/quản lý túi; test hai khách lấy túi cuối và hủy batch hủy đúng đơn |
| C / Order | Migration reservation_id/version/idempotency; state machine, snapshot giá/phí, scheduler | Cart/order/store order/pickup, payment intent, review eligibility; gọi Store policy + Catalog reservation HTTP | `finance.order-snapshots` consumer không thuộc C; C phát `order.state-changed.v1`, `order.refund-required.v1`, audit; C nhận `order.payment-results`, `order.refund-status`, `order.batch-cancelled` | FE giỏ/đơn/QR/giao; test timeout reservation, IPN lặp/muộn, hủy trả kho đúng một lần |
| D / Finance | Migration payment idempotency/attempt/version và refund version; snapshot/payout eligibility | VNPay URL/IPN, refund, payout, revenue, statement, dashboard + internal count HTTP | D nhận `finance.order-snapshots`, `finance.refund-required`; phát payment/refund events + audit; job đối soát tiền muộn | FE thanh toán/doanh thu/refund/payout/dashboard; test chữ ký IPN, refund UNIQUE, một đơn không vào hai payout |

**Việc chung trước Bước 3:** chạy `python -m pip install -r contracts/requirements.txt` rồi `python contracts/validate.py`; kiểm tra ví dụ event với schema. Triển khai integration test MySQL + PostgreSQL + RabbitMQ cho Outbox → publisher confirm/retry → queue → Inbox/DLQ theo quy định chung, sau đó mới đưa cơ chế dùng chung vào `building-blocks`. Gateway route và frontend API client được owner tính năng cập nhật ở Bước 4.
