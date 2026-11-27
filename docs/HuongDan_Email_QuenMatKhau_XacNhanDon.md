# Hướng dẫn triển khai email: quên mật khẩu và xác nhận đơn

## 1. Phạm vi và quyết định kiến trúc

Giữ nguyên 5 microservice. `identity-service` sở hữu tài khoản, email người dùng và việc gửi email qua SMTP `info@hoangnv.id.vn`. `order-service` chỉ phát `order.state-changed.v1` như hiện tại; Identity nhận sự kiện này để gửi thư khi đơn **chuyển sang `CONFIRMED` và `paymentStatus=PAID`**. Không gửi thư lúc tạo đơn `PENDING_PAYMENT` hoặc chỉ khi frontend hiện trang hoàn tất.

```text
Quên mật khẩu:
Browser -> Gateway -> Identity -> identity_db (reset token + email job)
                                      -> email worker -> SMTP -> hộp thư
Link trong thư -> Browser -> Gateway -> Identity -> đổi mật khẩu

Đơn đã thanh toán:
Finance -> payment.succeeded.v1 -> Order -> order_db + Outbox order.state-changed.v1
                                  -> RabbitMQ foodrescue.events
                                  -> identity.order-mails -> Identity Inbox + email job
                                  -> email worker -> SMTP -> hộp thư
```

`order.state-changed.v1` là snapshot trạng thái, có thể đến lặp hoặc sai thứ tự. Chỉ xử lý snapshot có `status=CONFIRMED`, `paymentStatus=PAID`; dùng khóa duy nhất `(template, order_id)` để mỗi đơn chỉ tạo một email xác nhận. Không đưa email khách vào Order event: Identity tra `users.email` theo `customerId` từ payload. Không đưa mã nhận hàng/QR vào email ở phiên bản đầu vì event hiện tại không chứa mã và không nên thêm dữ liệu nhạy cảm chỉ để phục vụ email. Thư có thể dẫn tới `/me/orders/{orderId}` để khách xem đơn sau khi đăng nhập.

## 2. Chuẩn bị SMTP

1. Lấy từ nhà cung cấp mail: SMTP host, cổng `587` STARTTLS hoặc `465` SSL, username, mật khẩu ứng dụng/SMTP, địa chỉ gửi `info@hoangnv.id.vn`. Có hộp thư đang dùng không đồng nghĩa đã bật quyền SMTP; thử đăng nhập SMTP trước.
2. Bảo đảm DNS của tên miền có SPF, DKIM và DMARC theo hướng dẫn của nhà cung cấp để thư ít vào spam.
3. Thêm vào `.env` cục bộ và `.env.example` **chỉ với giá trị mẫu**, không commit mật khẩu thật:

   ```dotenv
   MAIL_HOST=smtp.example.com
   MAIL_PORT=587
   MAIL_USERNAME=info@hoangnv.id.vn
   MAIL_PASSWORD=replace_with_smtp_password
   MAIL_FROM=info@hoangnv.id.vn
   APP_PUBLIC_URL=http://localhost:5173
   ```

4. Truyền các biến này cho `identity-service` trong `docker-compose.yml` và nhánh `identity-service` của `scripts/run-service.ps1`. `APP_PUBLIC_URL` phải là URL frontend mà người nhận mở được; trong môi trường public dùng HTTPS. Không dùng `localhost` trong email gửi cho người ở máy khác.
5. Thêm `spring-boot-starter-mail` vào `services/identity-service/pom.xml`. Trong `application.yml`, ánh xạ `spring.mail.host`, `port`, `username`, `password`, `properties.mail.smtp.auth=true`, `properties.mail.smtp.starttls.enable=true` cho cổng 587; đặt timeout kết nối/đọc/ghi. Nếu dùng cổng 465, cấu hình SSL theo thông số của nhà cung cấp thay vì bật STARTTLS như cổng 587.

## 3. Migration Identity

Tạo migration tiếp theo sau `V4__user_location.sql`, ví dụ `V5__password_reset_and_email_jobs.sql`, trên `identity_db`:

```sql
CREATE TABLE password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_reset_user (user_id, created_at),
    INDEX idx_reset_expiry (expires_at)
);

CREATE TABLE email_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template VARCHAR(40) NOT NULL,
    dedupe_key VARCHAR(120) NOT NULL,
    recipient VARCHAR(100) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NULL,
    sent_at DATETIME(6) NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_email_dedupe (template, dedupe_key),
    INDEX idx_email_pending (status, next_attempt_at, created_at)
);
```

Không lưu token thô vào `password_reset_tokens`; chỉ lưu SHA-256 của token ngẫu nhiên. URL chứa token nằm tạm trong `email_jobs.body` để worker gửi lại được: coi bảng này là dữ liệu nhạy cảm, giới hạn quyền truy cập DB/log và xóa nội dung của job reset sau khi gửi hoặc hết hạn. Token hết hạn sau 15–30 phút; chỉ dùng một lần. Với email đơn, `dedupe_key=order:{orderId}`. Với reset, tạo UUID riêng cho mỗi yêu cầu hợp lệ, ví dụ `reset:{tokenId}`.

## 4. API quên mật khẩu tại Identity

Thêm controller ở `api`, use case ở `application`, model/quy tắc ở `domain`, repository và SMTP adapter ở `infrastructure`; giữ cách đặt tên interface có tiền tố `I` của dự án.

| API | Quyền | Request | Response |
| --- | --- | --- | --- |
| `POST /api/auth/forgot-password` | Công khai | `{ "email": "user@example.com" }` | `202 { "message": "Nếu email tồn tại, chúng tôi sẽ gửi hướng dẫn đặt lại mật khẩu." }` |
| `POST /api/auth/reset-password` | Công khai | `{ "token": "...", "newPassword": "..." }` | `204 No Content`; token sai/hết hạn/đã dùng trả lỗi xác thực token chung |

Các bước cho `forgot-password`:

1. Chuẩn hóa email, giới hạn tần suất theo IP và email. Dù có hay không có tài khoản, trả cùng mã `202` và thông điệp để không lộ danh sách tài khoản.
2. Nếu user tồn tại và đang được phép đặt lại mật khẩu: tạo token bằng `SecureRandom` (ít nhất 32 byte), mã hóa URL-safe, lưu SHA-256 cùng hạn dùng. Vô hiệu các token reset cũ chưa dùng của user.
3. Trong **cùng transaction** tạo `password_reset_tokens` và `email_jobs(PENDING)` với link `${APP_PUBLIC_URL}/reset-password?token=...`. Không gọi SMTP trong transaction của API.
4. Không ghi token, URL đầy đủ hoặc mật khẩu vào log/audit. Có thể ghi audit `PASSWORD_RESET_REQUESTED` với `actor_id` null và `entity_id=userId` nếu chính sách audit yêu cầu.

Các bước cho `reset-password`:

1. Hash token từ body, tìm bản ghi còn hạn, chưa dùng và khóa row khi cập nhật để hai request đồng thời không cùng thắng.
2. Kiểm tra chính sách mật khẩu như đăng ký, hash bằng `IPasswordPort` hiện có; cập nhật `users.password_hash`, `must_change_password=false`, tăng `token_version` để JWT cũ hết hiệu lực.
3. Đánh dấu token đã dùng và vô hiệu token reset còn lại của user trong cùng transaction; ghi audit `PASSWORD_RESET_COMPLETED`. Không trả access token tự động; chuyển người dùng về trang login.
4. Thêm hai path vào `IdentitySecurity` và route `identity-api` của Gateway. Frontend thêm trang nhập email và trang đặt mật khẩu mới; màn hình đăng nhập liên kết tới trang quên mật khẩu.

`POST /api/admin/users/{id}/reset-password` đang là chức năng admin khác luồng tự phục hồi tài khoản. Không dùng API đó cho link email.

## 5. Email xác nhận đơn qua RabbitMQ

Không tạo event mới ở Order cho email. Dùng event đã có:

| Thành phần | Giá trị |
| --- | --- |
| Publisher | `order-service` Outbox sau khi xử lý `payment.succeeded.v1` |
| Exchange | `foodrescue.events` (`topic`, durable) |
| Routing key | `order.state-changed.v1` |
| Queue mới | `identity.order-mails` (durable), bind exact routing key |
| Consumer | `IdentityOrderMailListener` tại `identity-service` |
| Dữ liệu dùng | `eventId`, `aggregateVersion`, `payload.orderId`, `customerId`, `status`, `paymentStatus`, `totalAmount` |

Thêm tên queue/routing key vào `building-blocks/.../MessagingNames.java`. Tại `identity-service` khai báo queue, binding và DLQ theo mẫu `Identity` hiện có. Consumer kiểm tra event envelope và trạng thái; trong một transaction, `INSERT IGNORE` vào `inbox_events(consumer_name,event_id,processed_at)`, tra user theo `customerId`, tạo `email_jobs` với unique key `ORDER_CONFIRMED/order:{orderId}`. Nếu `eventId` đã xử lý hoặc job đã tồn tại thì ACK, không tạo thêm mail. Nếu có lỗi DB tạm thời thì để RabbitMQ retry/DLQ, không nuốt lỗi rồi ACK. Không gửi SMTP trực tiếp trong Rabbit listener.

Lưu ý: Finance hiện mới có nền DB/messaging; chỉ khi Finance thực sự phát `payment.succeeded.v1` và Order chuyển sang `CONFIRMED` thì mail này mới được tạo. Không coi việc browser bấm “Tôi đã chuyển khoản” là thanh toán thành công.

## 6. Worker gửi mail

Trong Identity, một scheduled worker đọc `email_jobs` còn `PENDING` và đến `next_attempt_at`, claim từng job bằng cập nhật có điều kiện/khóa row để nhiều instance không gửi cùng lúc, rồi dùng `JavaMailSender` qua adapter `ISmtpMailPort`.

- Thành công: đặt `SENT`, `sent_at`; với template reset, xóa `body` sau khi gửi để không lưu link lâu.
- SMTP lỗi tạm thời: tăng `attempts`, đặt `next_attempt_at` theo backoff, giữ `last_error` đã rút gọn và **không chứa thông tin đăng nhập/token**.
- Quá số lần thử: đặt `FAILED`, có log/metric để kiểm tra và thao tác retry có kiểm soát.
- Việc gửi SMTP và commit DB không thể atomic tuyệt đối; nếu process chết ngay sau khi SMTP nhận thư nhưng trước lúc DB ghi `SENT`, worker có thể gửi lại. Unique job ngăn tạo job trùng, nhưng không bảo đảm hộp thư nhận đúng một bản trong mọi sự cố.

Nội dung thư xác nhận nên có mã đơn, tổng tiền, tên cửa hàng và link `/me/orders/{id}`. Payload hiện chưa có tên cửa hàng; bản đầu chỉ cần mã đơn, tổng tiền và link, hoặc lấy tên bằng API nội bộ nếu thật sự cần. Không đưa token JWT vào email.

## 7. Trình tự triển khai và kiểm tra

1. Cấu hình SMTP và thử gửi một thư bằng Identity trong môi trường local; xác nhận SPF/DKIM/DMARC tại nhà cung cấp mail.
2. Thêm migration Identity; chạy service để Flyway tự áp dụng, kiểm tra hai bảng mới.
3. Làm worker gửi `email_jobs` và kiểm tra retry bằng SMTP tạm ngừng.
4. Làm API quên/đặt lại mật khẩu, Gateway và hai trang FE. Kiểm tra email có/không tồn tại đều trả `202`; token hết hạn, dùng hai lần, và JWT cũ sau reset.
5. Làm queue `identity.order-mails` và consumer tạo job. Publish thử một `order.state-changed.v1` với `CONFIRMED/PAID`; gửi lặp cùng `eventId` và event version mới để xác nhận chỉ có một job.
6. Chạy luồng thực từ Finance → Order → Identity. Tắt Identity, phát sự kiện, bật lại và xác nhận queue còn message, sau đó thư được gửi. Kiểm tra `PENDING_PAYMENT`, `CANCELLED`, `EXPIRED` không tạo thư xác nhận.

Không commit `.env` chứa thông tin SMTP thật. Chưa cần thêm microservice thứ sáu chỉ để gửi hai loại email này; khi khối lượng thư tăng mới cân nhắc tách notification-service.
