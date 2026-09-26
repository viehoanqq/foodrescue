-- =====================================================================
--  FOODRESCUE - Hệ thống giải cứu thực phẩm cuối ngày (bản rút gọn)
--  Script CSDL v5.4 - MySQL 8.0.16+ (utf8mb4) - 13 bảng
--  v5  : quản lý tiền rõ ràng (refunds, payouts theo kỳ gắn với đơn, tỉ lệ phí lưu theo đơn),
--        nhật ký thao tác (audit_logs)
--  v5.1: tỉ lệ phí theo cửa hàng, cửa hàng tự yêu cầu rút tiền, 1 chủ = 1 cửa hàng,
--        hoàn tiền khi thanh toán về muộn, ràng buộc nhân viên - cửa hàng
--  v5.2: giỏ hàng (cart_items), đơn nhiều túi (order_items), sổ kho (stock_movements)
--  v5.3: múi giờ +07:00, admin đặt lại mật khẩu tạm (must_change_password),
--        trigger chặn sửa/xoá sổ kho và nhật ký
--  v5.4: danh mục có ảnh và thứ tự hiển thị (thanh danh mục trang chủ)
--  Múi giờ: mọi cột DATETIME là giờ Việt Nam. Backend phải đặt cùng múi giờ
--        (JDBC serverTimezone=Asia/Ho_Chi_Minh, JVM -Duser.timezone=Asia/Ho_Chi_Minh).
--  Mật khẩu mặc định của mọi tài khoản mẫu: 123456 (BCrypt cost 10)
--  CẢNH BÁO: script xoá và tạo lại database foodrescuedb
-- =====================================================================
DROP DATABASE IF EXISTS foodrescuedb;
CREATE DATABASE foodrescuedb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE foodrescuedb;
SET time_zone = '+07:00';   -- NOW() trong phiên này theo giờ Việt Nam

-- ---------------------------------------------------------------------
-- 1. Người dùng (4 vai trò)
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    role           VARCHAR(20)  NOT NULL,                  -- ADMIN, STORE_OWNER, STORE_STAFF, CUSTOMER
    email          VARCHAR(100) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    full_name      VARCHAR(100) NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    store_id       BIGINT,                                 -- chỉ dùng cho STORE_STAFF
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, LOCKED
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,   -- TRUE: mật khẩu tạm (nhân viên mới / admin đặt lại)
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_role  CHECK (role IN ('ADMIN', 'STORE_OWNER', 'STORE_STAFF', 'CUSTOMER')),
    -- nhân viên bắt buộc thuộc 1 cửa hàng; vai trò khác không có store_id
    CONSTRAINT chk_users_staff CHECK ((role = 'STORE_STAFF') = (store_id IS NOT NULL))
);

-- ---------------------------------------------------------------------
-- 2. Cửa hàng
-- ---------------------------------------------------------------------
CREATE TABLE stores (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id          BIGINT        NOT NULL UNIQUE,   -- 1 chủ = 1 cửa hàng
    name              VARCHAR(150)  NOT NULL,
    description       TEXT,
    phone             VARCHAR(20)   NOT NULL,
    address           VARCHAR(255)  NOT NULL,
    latitude          DECIMAL(10,8) NOT NULL,        -- chủ quán ghim trên bản đồ
    longitude         DECIMAL(11,8) NOT NULL,
    image_url         VARCHAR(255),
    license_image_url VARCHAR(255)  NOT NULL,        -- ảnh giấy phép VSATTP
    open_time         TIME          NOT NULL,
    close_time        TIME          NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, REJECTED, LOCKED
    reject_reason     VARCHAR(255),
    commission_rate   DECIMAL(5,2)  NOT NULL DEFAULT 15.00,  -- % phí nền tảng, admin đặt; đơn mới copy sang orders
    bank_name         VARCHAR(100)  NOT NULL,        -- tài khoản nhận tiền chi trả (bắt buộc)
    bank_account_no   VARCHAR(30)   NOT NULL,
    bank_account_name VARCHAR(100)  NOT NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stores_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT chk_store_time  CHECK (close_time > open_time),
    CONSTRAINT chk_store_rate  CHECK (commission_rate BETWEEN 0 AND 50)
);
CREATE INDEX idx_stores_status_geo ON stores(status, latitude, longitude);

ALTER TABLE users ADD CONSTRAINT fk_users_store FOREIGN KEY (store_id) REFERENCES stores(id);

-- ---------------------------------------------------------------------
-- 3. Danh mục
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    image_url   VARCHAR(255),                    -- ảnh vuông nền trong, hiện trên thanh danh mục
    sort_order  INT          NOT NULL DEFAULT 0  -- nhỏ đứng trước
);

-- ---------------------------------------------------------------------
-- 4. Túi giải cứu (hàng đăng bán = tồn kho hiện tại)
-- ---------------------------------------------------------------------
CREATE TABLE rescue_batches (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id            BIGINT        NOT NULL,
    category_id         BIGINT        NOT NULL,
    title               VARCHAR(150)  NOT NULL,
    description         TEXT,
    image_url           VARCHAR(255),
    original_price      DECIMAL(12,0) NOT NULL,
    rescue_price        DECIMAL(12,0) NOT NULL,
    quantity            INT           NOT NULL,      -- tổng số túi đã đưa lên app = đã bán + còn lại
    remaining_quantity  INT           NOT NULL,      -- còn lại để bán
    pickup_start        DATETIME      NOT NULL,
    pickup_end          DATETIME      NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE, SOLD_OUT, EXPIRED, CANCELLED
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_batch_store    FOREIGN KEY (store_id)    REFERENCES stores(id),
    CONSTRAINT fk_batch_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT chk_batch_price   CHECK (rescue_price > 0 AND rescue_price <= original_price * 0.6),
    CONSTRAINT chk_batch_qty     CHECK (quantity >= 0 AND remaining_quantity BETWEEN 0 AND quantity),
    CONSTRAINT chk_batch_time    CHECK (pickup_end > pickup_start)
);
CREATE INDEX idx_batch_listing ON rescue_batches(status, pickup_end);

-- ---------------------------------------------------------------------
-- 5. Kỳ chi trả cho cửa hàng: admin tạo theo kỳ, HOẶC chủ cửa hàng bấm "Yêu cầu rút tiền"
--    (created_by = người tạo). Gom các đơn chưa chi trong kỳ.
-- ---------------------------------------------------------------------
CREATE TABLE payouts (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    payout_code       VARCHAR(30)   NOT NULL UNIQUE,     -- PO-<storeId>-yyyyMMdd
    store_id          BIGINT        NOT NULL,
    period_from       DATE          NOT NULL,            -- kỳ đối soát (theo ngày chốt đơn)
    period_to         DATE          NOT NULL,
    order_count       INT           NOT NULL,
    gross_amount      DECIMAL(14,0) NOT NULL,            -- tổng khách đã trả của các đơn trong kỳ
    commission_amount DECIMAL(14,0) NOT NULL,            -- tổng phí nền tảng
    net_amount        DECIMAL(14,0) NOT NULL,            -- số tiền chuyển cho cửa hàng
    bank_name         VARCHAR(100)  NOT NULL,            -- chụp lại tài khoản tại thời điểm tạo
    bank_account_no   VARCHAR(30)   NOT NULL,
    bank_account_name VARCHAR(100)  NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING, PAID, CANCELLED
    transfer_ref      VARCHAR(100),                      -- mã giao dịch chuyển khoản ngân hàng
    note              VARCHAR(255),
    created_by        BIGINT        NOT NULL,            -- admin, hoặc chủ cửa hàng (yêu cầu rút tiền)
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_by           BIGINT,
    paid_at           DATETIME,
    CONSTRAINT fk_payouts_store   FOREIGN KEY (store_id)   REFERENCES stores(id),
    CONSTRAINT fk_payouts_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_payouts_payer   FOREIGN KEY (paid_by)    REFERENCES users(id),
    CONSTRAINT chk_payout_money   CHECK (order_count > 0 AND net_amount > 0
                                         AND net_amount = gross_amount - commission_amount),
    CONSTRAINT chk_payout_period  CHECK (period_to >= period_from),
    CONSTRAINT chk_payout_paid    CHECK (status <> 'PAID' OR (paid_at IS NOT NULL AND transfer_ref IS NOT NULL))
);

-- ---------------------------------------------------------------------
-- 6. Giỏ hàng (lưu trong CSDL; chỉ chứa túi của 1 cửa hàng - kiểm tra ở service)
--    Giỏ hàng KHÔNG giữ hàng; hàng chỉ bị trừ khi đặt đơn.
-- ---------------------------------------------------------------------
CREATE TABLE cart_items (
    customer_id  BIGINT   NOT NULL,
    batch_id     BIGINT   NOT NULL,
    quantity     INT      NOT NULL,
    added_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (customer_id, batch_id),
    CONSTRAINT fk_cart_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_cart_batch    FOREIGN KEY (batch_id)    REFERENCES rescue_batches(id),
    CONSTRAINT chk_cart_qty     CHECK (quantity BETWEEN 1 AND 5)
);

-- ---------------------------------------------------------------------
-- 7. Đơn hàng (1 đơn = 1 cửa hàng, nhiều túi)
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_code         VARCHAR(30)   NOT NULL UNIQUE,
    customer_id        BIGINT        NOT NULL,
    store_id           BIGINT        NOT NULL,
    item_count         INT           NOT NULL,     -- tổng số túi trong đơn
    total_amount       DECIMAL(12,0) NOT NULL,     -- = tổng order_items.subtotal (khách trả)
    commission_rate    DECIMAL(5,2)  NOT NULL,     -- tỉ lệ phí của cửa hàng tại thời điểm đặt, vd 15.00
    commission_amount  DECIMAL(12,0) NOT NULL,     -- = ROUND(total_amount * commission_rate / 100)
    store_earning      DECIMAL(12,0) NOT NULL,     -- = total_amount - commission_amount
    pickup_start       DATETIME      NOT NULL,     -- khung giờ lấy của đơn = phần giao nhau
    pickup_end         DATETIME      NOT NULL,     --   khung giờ của các túi trong đơn
    pickup_code        VARCHAR(10)   NOT NULL UNIQUE,
    status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING_PAYMENT',
        -- PENDING_PAYMENT, CONFIRMED, COMPLETED, CANCELLED, EXPIRED, NO_SHOW
        -- CANCELLED + UNPAID: khách tự huỷ đơn chưa trả tiền; CANCELLED + REFUND_*: đã trả tiền rồi bị huỷ
    payment_status     VARCHAR(20)   NOT NULL DEFAULT 'UNPAID',
        -- UNPAID, PAID, REFUND_PENDING, REFUNDED
    payout_id          BIGINT,                     -- kỳ chi trả đã gom đơn này (NULL = chưa chi)
    cancel_reason      VARCHAR(255),
    cancelled_by       BIGINT,
    cancelled_at       DATETIME,
    handled_by         BIGINT,                     -- người giao hàng (chủ/nhân viên)
    payment_expires_at DATETIME      NOT NULL,     -- link VNPay hết hạn sau 10 phút
    completed_at       DATETIME,                   -- thời điểm chốt đơn: giao hàng (COMPLETED) hoặc NO_SHOW
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_customer  FOREIGN KEY (customer_id)  REFERENCES users(id),
    CONSTRAINT fk_orders_store     FOREIGN KEY (store_id)     REFERENCES stores(id),
    CONSTRAINT fk_orders_payout    FOREIGN KEY (payout_id)    REFERENCES payouts(id),
    CONSTRAINT fk_orders_canceller FOREIGN KEY (cancelled_by) REFERENCES users(id),
    CONSTRAINT fk_orders_handler   FOREIGN KEY (handled_by)   REFERENCES users(id),
    CONSTRAINT chk_order_items     CHECK (item_count > 0),
    CONSTRAINT chk_order_money     CHECK (total_amount > 0
                                          AND commission_amount = ROUND(total_amount * commission_rate / 100)
                                          AND store_earning = total_amount - commission_amount),
    CONSTRAINT chk_order_pickup    CHECK (pickup_end > pickup_start),
    -- chỉ đơn đã hoàn tất / không đến lấy mới được đưa vào kỳ chi trả
    CONSTRAINT chk_order_payout    CHECK (payout_id IS NULL OR status IN ('COMPLETED', 'NO_SHOW')),
    CONSTRAINT chk_order_done_time CHECK ((status IN ('COMPLETED', 'NO_SHOW')) = (completed_at IS NOT NULL))
);
CREATE INDEX idx_orders_store    ON orders(store_id, status, created_at);
CREATE INDEX idx_orders_customer ON orders(customer_id, created_at);
CREATE INDEX idx_orders_payout   ON orders(store_id, payout_id, status, completed_at);

-- ---------------------------------------------------------------------
-- 8. Chi tiết đơn (các túi trong đơn, giá chụp lại lúc mua)
-- ---------------------------------------------------------------------
CREATE TABLE order_items (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT        NOT NULL,
    batch_id    BIGINT        NOT NULL,
    quantity    INT           NOT NULL,
    unit_price  DECIMAL(12,0) NOT NULL,       -- giá giải cứu tại thời điểm mua
    subtotal    DECIMAL(12,0) NOT NULL,       -- = unit_price * quantity
    CONSTRAINT fk_items_order  FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_items_batch  FOREIGN KEY (batch_id) REFERENCES rescue_batches(id),
    CONSTRAINT uq_items_batch  UNIQUE (order_id, batch_id),
    CONSTRAINT chk_items_qty   CHECK (quantity BETWEEN 1 AND 5),
    CONSTRAINT chk_items_money CHECK (subtotal = unit_price * quantity)
);

-- ---------------------------------------------------------------------
-- 9. Sổ kho: mỗi lần số lượng túi thay đổi ghi 1 dòng (chỉ thêm, không sửa/xoá)
--    quantity  = tổng change_qty của LISTED + ADDED + REMOVED
--    remaining = tổng change_qty của mọi dòng
-- ---------------------------------------------------------------------
CREATE TABLE stock_movements (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id        BIGINT       NOT NULL,
    change_qty      INT          NOT NULL,        -- dương = tăng, âm = giảm
    reason          VARCHAR(20)  NOT NULL,
        -- LISTED  : đăng túi (+ số lượng ban đầu)
        -- ADDED   : cửa hàng thêm túi (+)
        -- REMOVED : bớt túi, vd khách tại quầy mua mất (−)
        -- ORDERED : khách đặt đơn (−)
        -- RETURNED: đơn hết hạn / huỷ, trả lại (+)
    order_id        BIGINT,                       -- với ORDERED / RETURNED
    actor_id        BIGINT,                       -- NULL = hệ thống
    remaining_after INT          NOT NULL,        -- số còn lại sau thay đổi
    note            VARCHAR(255),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_batch FOREIGN KEY (batch_id) REFERENCES rescue_batches(id),
    CONSTRAINT fk_stock_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_stock_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT chk_stock_nonzero CHECK (change_qty <> 0),
    CONSTRAINT chk_stock_sign CHECK (
        (reason IN ('LISTED', 'ADDED', 'RETURNED') AND change_qty > 0) OR
        (reason IN ('REMOVED', 'ORDERED')          AND change_qty < 0)),
    CONSTRAINT chk_stock_order CHECK ((reason IN ('ORDERED', 'RETURNED')) = (order_id IS NOT NULL)),
    CONSTRAINT chk_stock_after CHECK (remaining_after >= 0)
);
CREATE INDEX idx_stock_batch ON stock_movements(batch_id, created_at);

-- Sổ kho chỉ được thêm, không được sửa / xoá
DELIMITER $$
CREATE TRIGGER trg_stock_no_update BEFORE UPDATE ON stock_movements FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'stock_movements chỉ được thêm, không được sửa'$$
CREATE TRIGGER trg_stock_no_delete BEFORE DELETE ON stock_movements FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'stock_movements chỉ được thêm, không được xoá'$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- 10. Giao dịch thanh toán VNPay
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id         BIGINT        NOT NULL,
    amount           DECIMAL(12,0) NOT NULL,
    txn_ref          VARCHAR(64)   NOT NULL UNIQUE,   -- vnp_TxnRef
    gateway_txn_no   VARCHAR(64),                     -- vnp_TransactionNo (cần khi hoàn tiền)
    bank_code        VARCHAR(20),
    response_code    VARCHAR(10),                     -- vnp_ResponseCode
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING, SUCCESS, FAILED
    paid_at          DATETIME,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

-- ---------------------------------------------------------------------
-- 11. Hoàn tiền cho khách
-- ---------------------------------------------------------------------
CREATE TABLE refunds (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_code      VARCHAR(30)   NOT NULL UNIQUE,   -- RF-<orderCode>
    order_id         BIGINT        NOT NULL UNIQUE,   -- mỗi đơn hoàn tối đa 1 lần (toàn bộ)
    payment_id       BIGINT        NOT NULL,          -- giao dịch VNPay gốc
    amount           DECIMAL(12,0) NOT NULL,          -- = orders.total_amount
    reason           VARCHAR(30)   NOT NULL,          -- STORE_CANCELLED, BATCH_CANCELLED, ADMIN_CANCELLED, LATE_PAYMENT
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING, COMPLETED
    gateway_ref      VARCHAR(100),                    -- mã giao dịch hoàn trên VNPay merchant
    note             VARCHAR(255),
    requested_by     BIGINT,                          -- người huỷ đơn; NULL = hệ thống (LATE_PAYMENT)
    requested_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_by     BIGINT,                          -- admin xác nhận đã hoàn
    processed_at     DATETIME,
    CONSTRAINT fk_refunds_order     FOREIGN KEY (order_id)     REFERENCES orders(id),
    CONSTRAINT fk_refunds_payment   FOREIGN KEY (payment_id)   REFERENCES payments(id),
    CONSTRAINT fk_refunds_requester FOREIGN KEY (requested_by) REFERENCES users(id),
    CONSTRAINT fk_refunds_processor FOREIGN KEY (processed_by) REFERENCES users(id),
    CONSTRAINT chk_refund_amount    CHECK (amount > 0),
    CONSTRAINT chk_refund_done      CHECK (status <> 'COMPLETED' OR (processed_at IS NOT NULL AND gateway_ref IS NOT NULL))
);

-- ---------------------------------------------------------------------
-- 12. Đánh giá (1 đơn 1 đánh giá, đánh giá cửa hàng)
-- ---------------------------------------------------------------------
CREATE TABLE reviews (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT   NOT NULL UNIQUE,
    customer_id  BIGINT   NOT NULL,
    store_id     BIGINT   NOT NULL,
    rating       TINYINT  NOT NULL,
    comment      TEXT,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reviews_order    FOREIGN KEY (order_id)    REFERENCES orders(id),
    CONSTRAINT fk_reviews_customer FOREIGN KEY (customer_id) REFERENCES users(id),
    CONSTRAINT fk_reviews_store    FOREIGN KEY (store_id)    REFERENCES stores(id),
    CONSTRAINT chk_review_rating   CHECK (rating BETWEEN 1 AND 5)
);

-- ---------------------------------------------------------------------
-- 13. Nhật ký thao tác (chỉ thêm, không sửa/xoá)
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id     BIGINT,                              -- NULL = hệ thống (VNPay, tác vụ tự động)
    action       VARCHAR(40)  NOT NULL,
        -- PAYMENT_SUCCESS, ORDER_CANCELLED, ORDER_EXPIRED, ORDER_COMPLETED, ORDER_NO_SHOW,
        -- REFUND_REQUESTED, REFUND_COMPLETED, PAYOUT_CREATED, PAYOUT_PAID, PAYOUT_CANCELLED,
        -- BATCH_CANCELLED, STORE_APPROVED, STORE_REJECTED, STORE_LOCKED, STORE_RATE_CHANGED,
        -- USER_LOCKED, USER_UNLOCKED, USER_PASSWORD_RESET
    entity_type  VARCHAR(20)  NOT NULL,               -- ORDER, REFUND, PAYOUT, STORE, USER, BATCH
    entity_id    BIGINT       NOT NULL,
    amount       DECIMAL(14,0),                       -- số tiền liên quan (nếu có)
    detail       VARCHAR(500),
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users(id)
);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_time   ON audit_logs(created_at);

-- Nhật ký chỉ được thêm, không được sửa / xoá
DELIMITER $$
CREATE TRIGGER trg_audit_no_update BEFORE UPDATE ON audit_logs FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_logs chỉ được thêm, không được sửa'$$
CREATE TRIGGER trg_audit_no_delete BEFORE DELETE ON audit_logs FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_logs chỉ được thêm, không được xoá'$$
DELIMITER ;

-- =====================================================================
--  DỮ LIỆU MẪU ("hôm nay" = 2026-09-25)
-- =====================================================================
SET @pw = '$2a$10$Z9WS6Xe2NDqRK7M4rKoyV.uxSnXxsllWiVAbVXxa4tvQyn1nxeNni';  -- 123456
INSERT INTO users (id, role, email, password_hash, full_name, phone, status) VALUES
( 1, 'ADMIN',       'admin@foodrescue.vn',        @pw, 'Quản Trị Viên',   '0909000001', 'ACTIVE'),
( 2, 'STORE_OWNER', 'owner.anphat@gmail.com',     @pw, 'Nguyễn Văn Phát', '0912345671', 'ACTIVE'),
( 3, 'STORE_OWNER', 'owner.sweetparis@gmail.com', @pw, 'Lê Thị Thu Thảo', '0912345672', 'ACTIVE'),
( 4, 'STORE_OWNER', 'owner.comtam365@gmail.com',  @pw, 'Trần Văn Cali',   '0912345673', 'ACTIVE'),
( 5, 'STORE_OWNER', 'owner.greenmart@gmail.com',  @pw, 'Võ Thị Hạnh',     '0912345674', 'ACTIVE'),
( 8, 'CUSTOMER',    'an.malyhoang@gmail.com',     @pw, 'Ma Lý Hoàng Ân',  '0987654321', 'ACTIVE'),
( 9, 'CUSTOMER',    'linh.tranhoang@gmail.com',   @pw, 'Trần Hoàng Linh', '0987654322', 'ACTIVE'),
(10, 'CUSTOMER',    'bao.lequoc@gmail.com',       @pw, 'Lê Quốc Bảo',     '0987654323', 'ACTIVE'),
(11, 'CUSTOMER',    'thu.phamminh@gmail.com',     @pw, 'Phạm Minh Thư',   '0987654324', 'ACTIVE');

INSERT INTO stores (id, owner_id, name, description, phone, address, latitude, longitude, license_image_url,
                    open_time, close_time, status, commission_rate, bank_name, bank_account_no, bank_account_name) VALUES
(1, 2, 'Bánh Mì Tươi & Hamburger An Phát', 'Bánh mì, sandwich, hamburger làm trong ngày.', '02838300001',
    '105 An Dương Vương, Phường 8, Quận 5, TP.HCM', 10.75837200, 106.67123900, '/uploads/license-1.jpg',
    '06:30:00', '21:30:00', 'APPROVED', 15.00, 'Vietcombank', '0071000123456', 'NGUYEN VAN PHAT'),
(2, 3, 'Sweet Paris Bakery & Coffee', 'Bánh ngọt kiểu Pháp và cà phê.', '02838300002',
    '45 Lê Duẩn, Bến Nghé, Quận 1, TP.HCM', 10.78125400, 106.69894200, '/uploads/license-2.jpg',
    '07:00:00', '22:00:00', 'APPROVED', 15.00, 'Techcombank', '19031234567890', 'LE THI THU THAO'),
(3, 4, 'Cơm Tấm Truyền Thống Sài Gòn 365', 'Cơm tấm sườn bì chả truyền thống.', '02838300003',
    '215 Điện Biên Phủ, Phường 15, Bình Thạnh, TP.HCM', 10.79634100, 106.70921800, '/uploads/license-3.jpg',
    '10:00:00', '21:00:00', 'APPROVED', 15.00, 'ACB', '2468101214', 'TRAN VAN CALI'),
(4, 5, 'GreenMart Rau Củ Sạch', 'Rau củ quả hữu cơ.', '02838300004',
    '12 Nguyễn Thị Minh Khai, Đa Kao, Quận 1, TP.HCM', 10.78756000, 106.69980000, '/uploads/license-4.jpg',
    '07:00:00', '21:00:00', 'PENDING', 10.00, 'MB Bank', '0123456789', 'VO THI HANH');

-- Nhân viên chèn sau cửa hàng (vì bắt buộc có store_id)
INSERT INTO users (id, role, email, password_hash, full_name, phone, store_id, status) VALUES
( 6, 'STORE_STAFF', 'staff.anphat@gmail.com',     @pw, 'Đỗ Minh Khoa',    '0933000001', 1, 'ACTIVE'),   -- cả 2 đã đổi mật khẩu
( 7, 'STORE_STAFF', 'staff.sweetparis@gmail.com', @pw, 'Huỳnh Ngọc Mai',  '0933000002', 2, 'ACTIVE');

INSERT INTO categories (id, name, image_url, sort_order) VALUES
(1, 'Bánh mì & Sandwich', '/uploads/categories/banh-mi.png',   1),
(2, 'Bánh ngọt',          '/uploads/categories/banh-ngot.png',  2),
(3, 'Cơm & Món chính',    '/uploads/categories/com.png',        3),
(4, 'Rau củ & Trái cây',  '/uploads/categories/rau-cu.png',     4),
(5, 'Đồ uống',            '/uploads/categories/do-uong.png',    5),
(6, 'Tạp hoá',            '/uploads/categories/tap-hoa.png',    6);

INSERT INTO rescue_batches (id, store_id, category_id, title, description, original_price, rescue_price,
       quantity, remaining_quantity, pickup_start, pickup_end, status, created_at) VALUES
(1, 1, 1, 'Túi Bánh Mì Bất Ngờ', '2 bánh mì hoa cúc mini + 1 sandwich xúc xích trong ngày.',
    80000, 30000, 10, 8, '2026-09-25 20:30:00', '2026-09-25 21:30:00', 'AVAILABLE', '2026-09-25 09:00:00'),
(2, 1, 1, 'Combo Hamburger Cuối Giờ', '2 burger bò/gà giữ nóng tại quầy.',
    95000, 40000, 5, 0, '2026-09-23 20:30:00', '2026-09-23 21:30:00', 'SOLD_OUT', '2026-09-23 17:00:00'),
(3, 2, 2, 'Surprise Box Bánh Pháp', 'Ngẫu nhiên 3 loại: croissant phô mai, tart trứng, donut kem.',
    120000, 45000, 15, 14, '2026-09-25 21:00:00', '2026-09-25 22:00:00', 'AVAILABLE', '2026-09-25 08:00:00'),
(4, 2, 2, 'Bánh Kem Lát Mini', 'Bánh kem bắp và tiramisu lát, bảo quản tủ mát.',
    110000, 42000, 8, 6, '2026-09-25 21:00:00', '2026-09-25 22:00:00', 'AVAILABLE', '2026-09-25 08:05:00'),
(5, 3, 3, 'Suất Cơm Sườn Bì Chả', 'Cơm nóng đóng hộp gồm sườn nướng, bì, chả trứng.',
    65000, 25000, 20, 18, '2026-09-25 20:00:00', '2026-09-25 21:00:00', 'AVAILABLE', '2026-09-25 10:00:00'),
(6, 3, 3, 'Cơm Tấm Cuối Ngày', 'Suất cơm tấm sườn trứng.',
    60000, 22000, 10, 9, '2026-09-23 20:00:00', '2026-09-23 21:00:00', 'EXPIRED', '2026-09-23 16:00:00');

-- Kỳ chi trả: An Phát đã được chi kỳ 21-24/09 (đơn 1, 2). Cơm Tấm 365 chưa được chi (đơn 3).
INSERT INTO payouts (id, payout_code, store_id, period_from, period_to, order_count, gross_amount,
       commission_amount, net_amount, bank_name, bank_account_no, bank_account_name, status,
       transfer_ref, note, created_by, created_at, paid_by, paid_at) VALUES
(1, 'PO-1-20260924', 1, '2026-09-21', '2026-09-24', 2, 200000, 30000, 170000,
    'Vietcombank', '0071000123456', 'NGUYEN VAN PHAT', 'PAID',
    'VCB-FT26267112233', 'Chi trả tuần 21-24/09', 1, '2026-09-25 09:00:00', 1, '2026-09-25 09:30:00');

-- Đơn hàng (phí 15%). Đơn 4 gồm 2 loại túi của Sweet Paris (đặt từ giỏ hàng).
INSERT INTO orders (id, order_code, customer_id, store_id, item_count, total_amount,
       commission_rate, commission_amount, store_earning, pickup_start, pickup_end, pickup_code,
       status, payment_status, payout_id, cancel_reason, cancelled_by, cancelled_at, handled_by,
       payment_expires_at, completed_at, created_at) VALUES
(1, 'FR260923-0001',  8, 1, 2,  80000, 15.00, 12000,  68000, '2026-09-23 20:30:00', '2026-09-23 21:30:00', 'K7Q2M9', 'COMPLETED',       'PAID',           1,    NULL, NULL, NULL, 6,    '2026-09-23 19:25:00', '2026-09-23 20:45:00', '2026-09-23 19:15:00'),
(2, 'FR260923-0002',  9, 1, 3, 120000, 15.00, 18000, 102000, '2026-09-23 20:30:00', '2026-09-23 21:30:00', 'P3X8D1', 'COMPLETED',       'PAID',           1,    NULL, NULL, NULL, 6,    '2026-09-23 19:40:00', '2026-09-23 21:10:00', '2026-09-23 19:30:00'),
(3, 'FR260923-0003', 11, 3, 1,  22000, 15.00,  3300,  18700, '2026-09-23 20:00:00', '2026-09-23 21:00:00', 'T5N6B4', 'NO_SHOW',         'PAID',           NULL, NULL, NULL, NULL, NULL, '2026-09-23 18:10:00', '2026-09-23 21:30:00', '2026-09-23 18:00:00'),
(4, 'FR260925-0004',  8, 2, 2,  87000, 15.00, 13050,  73950, '2026-09-25 21:00:00', '2026-09-25 22:00:00', 'H2R9W7', 'CONFIRMED',       'PAID',           NULL, NULL, NULL, NULL, NULL, '2026-09-25 10:30:00', NULL,                  '2026-09-25 10:20:00'),
(5, 'FR260925-0005',  9, 3, 2,  50000, 15.00,  7500,  42500, '2026-09-25 20:00:00', '2026-09-25 21:00:00', 'M8C1Z5', 'CONFIRMED',       'PAID',           NULL, NULL, NULL, NULL, NULL, '2026-09-25 11:55:00', NULL,                  '2026-09-25 11:45:00'),
(6, 'FR260925-0006', 10, 1, 2,  60000, 15.00,  9000,  51000, '2026-09-25 20:30:00', '2026-09-25 21:30:00', 'W4E7J3', 'CONFIRMED',       'PAID',           NULL, NULL, NULL, NULL, NULL, '2026-09-25 12:20:00', NULL,                  '2026-09-25 12:10:00'),
(7, 'FR260925-0007', 11, 2, 1,  45000, 15.00,  6750,  38250, '2026-09-25 21:00:00', '2026-09-25 22:00:00', 'Y6U3S8', 'CANCELLED',       'REFUND_PENDING', NULL, 'Khách tại quầy đã mua hết', 3, '2026-09-25 13:20:00', NULL, '2026-09-25 13:10:00', NULL, '2026-09-25 13:00:00'),
(8, 'FR260925-0008', 10, 2, 1,  42000, 15.00,  6300,  35700, '2026-09-25 21:00:00', '2026-09-25 22:00:00', 'G9L2V6', 'PENDING_PAYMENT', 'UNPAID',         NULL, NULL, NULL, NULL, NULL, '2026-09-25 13:40:00', NULL,                  '2026-09-25 13:30:00');

INSERT INTO order_items (order_id, batch_id, quantity, unit_price, subtotal) VALUES
(1, 2, 2, 40000,  80000),
(2, 2, 3, 40000, 120000),
(3, 6, 1, 22000,  22000),
(4, 3, 1, 45000,  45000),
(4, 4, 1, 42000,  42000),
(5, 5, 2, 25000,  50000),
(6, 1, 2, 30000,  60000),
(7, 3, 1, 45000,  45000),
(8, 4, 1, 42000,  42000);

-- Sổ kho (khớp với quantity / remaining_quantity ở trên)
INSERT INTO stock_movements (batch_id, change_qty, reason, order_id, actor_id, remaining_after, note, created_at) VALUES
(6,  10, 'LISTED',   NULL, 4,    10, 'Đăng túi',                    '2026-09-23 16:00:00'),
(2,   5, 'LISTED',   NULL, 2,     5, 'Đăng túi',                    '2026-09-23 17:00:00'),
(6,  -1, 'ORDERED',  3,    11,    9, 'Đơn FR260923-0003',           '2026-09-23 18:00:00'),
(2,  -2, 'ORDERED',  1,    8,     3, 'Đơn FR260923-0001',           '2026-09-23 19:15:00'),
(2,  -3, 'ORDERED',  2,    9,     0, 'Đơn FR260923-0002',           '2026-09-23 19:30:00'),
(3,  15, 'LISTED',   NULL, 7,    15, 'Đăng túi',                    '2026-09-25 08:00:00'),
(4,   8, 'LISTED',   NULL, 3,     8, 'Đăng túi',                    '2026-09-25 08:05:00'),
(1,   8, 'LISTED',   NULL, 6,     8, 'Đăng túi',                    '2026-09-25 09:00:00'),
(5,  20, 'LISTED',   NULL, 4,    20, 'Đăng túi',                    '2026-09-25 10:00:00'),
(3,  -1, 'ORDERED',  4,    8,    14, 'Đơn FR260925-0004',           '2026-09-25 10:20:00'),
(4,  -1, 'ORDERED',  4,    8,     7, 'Đơn FR260925-0004',           '2026-09-25 10:20:00'),
(1,   2, 'ADDED',    NULL, 6,    10, 'Làm thêm 2 túi',              '2026-09-25 11:00:00'),
(5,  -2, 'ORDERED',  5,    9,    18, 'Đơn FR260925-0005',           '2026-09-25 11:45:00'),
(1,  -2, 'ORDERED',  6,    10,    8, 'Đơn FR260925-0006',           '2026-09-25 12:10:00'),
(3,  -1, 'ORDERED',  7,    11,   13, 'Đơn FR260925-0007',           '2026-09-25 13:00:00'),
(3,   1, 'RETURNED', 7,    3,    14, 'Huỷ đơn FR260925-0007',       '2026-09-25 13:20:00'),
(4,  -1, 'ORDERED',  8,    10,    6, 'Đơn FR260925-0008',           '2026-09-25 13:30:00');

-- Giỏ hàng hiện tại: Linh đang chọn 2 túi của Sweet Paris; Bảo đang chọn 1 túi An Phát
INSERT INTO cart_items (customer_id, batch_id, quantity, added_at) VALUES
( 9, 3, 1, '2026-09-25 14:00:00'),
( 9, 4, 2, '2026-09-25 14:01:00'),
(10, 1, 1, '2026-09-25 14:05:00');

INSERT INTO payments (id, order_id, amount, txn_ref, gateway_txn_no, bank_code, response_code, status, paid_at) VALUES
(1, 1, 80000,  'FR260923-0001-1', '14567001', 'NCB', '00', 'SUCCESS', '2026-09-23 19:16:00'),
(2, 2, 120000, 'FR260923-0002-1', '14567002', 'NCB', '00', 'SUCCESS', '2026-09-23 19:31:00'),
(3, 3, 22000,  'FR260923-0003-1', '14567003', 'NCB', '00', 'SUCCESS', '2026-09-23 18:01:00'),
(4, 4, 87000,  'FR260925-0004-1', '14567004', 'NCB', '00', 'SUCCESS', '2026-09-25 10:21:00'),
(5, 5, 50000,  'FR260925-0005-1', '14567005', 'NCB', '00', 'SUCCESS', '2026-09-25 11:46:00'),
(6, 6, 60000,  'FR260925-0006-1', '14567006', 'NCB', '00', 'SUCCESS', '2026-09-25 12:11:00'),
(7, 7, 45000,  'FR260925-0007-1', '14567007', 'NCB', '00', 'SUCCESS', '2026-09-25 13:01:00'),
(8, 8, 42000,  'FR260925-0008-1', NULL,       NULL,  NULL, 'PENDING', NULL);

INSERT INTO refunds (refund_code, order_id, payment_id, amount, reason, status, requested_by, requested_at) VALUES
('RF-FR260925-0007', 7, 7, 45000, 'STORE_CANCELLED', 'PENDING', 3, '2026-09-25 13:20:00');

INSERT INTO reviews (order_id, customer_id, store_id, rating, comment, created_at) VALUES
(1, 8, 1, 5, 'Burger còn nóng, bánh mềm. Giá giải cứu quá rẻ, ủng hộ mô hình chống lãng phí!', '2026-09-23 21:40:00'),
(2, 9, 1, 4, 'Ngon, nhưng 1 cái burger hơi nguội.', '2026-09-23 21:50:00');

INSERT INTO audit_logs (actor_id, action, entity_type, entity_id, amount, detail, created_at) VALUES
(1,    'STORE_APPROVED',   'STORE',  1, NULL,   'Duyệt cửa hàng An Phát, phí 15%',                '2026-09-01 09:00:00'),
(1,    'STORE_APPROVED',   'STORE',  2, NULL,   'Duyệt cửa hàng Sweet Paris, phí 15%',            '2026-09-02 10:00:00'),
(1,    'STORE_APPROVED',   'STORE',  3, NULL,   'Duyệt cửa hàng Cơm Tấm 365, phí 15%',            '2026-09-03 14:00:00'),
(NULL, 'PAYMENT_SUCCESS',  'ORDER',  3, 22000,  'VNPay 14567003',                                 '2026-09-23 18:01:00'),
(NULL, 'PAYMENT_SUCCESS',  'ORDER',  1, 80000,  'VNPay 14567001',                                 '2026-09-23 19:16:00'),
(NULL, 'PAYMENT_SUCCESS',  'ORDER',  2, 120000, 'VNPay 14567002',                                 '2026-09-23 19:31:00'),
(6,    'ORDER_COMPLETED',  'ORDER',  1, 80000,  'Giao hàng mã K7Q2M9',                            '2026-09-23 20:45:00'),
(6,    'ORDER_COMPLETED',  'ORDER',  2, 120000, 'Giao hàng mã P3X8D1',                            '2026-09-23 21:10:00'),
(NULL, 'ORDER_NO_SHOW',    'ORDER',  3, 22000,  'Quá giờ nhận 30 phút',                           '2026-09-23 21:30:00'),
(1,    'PAYOUT_CREATED',   'PAYOUT', 1, 170000, 'Kỳ 21-24/09, 2 đơn',                             '2026-09-25 09:00:00'),
(1,    'PAYOUT_PAID',      'PAYOUT', 1, 170000, 'Chuyển khoản VCB-FT26267112233',                 '2026-09-25 09:30:00'),
(NULL, 'PAYMENT_SUCCESS',  'ORDER',  4, 87000,  'VNPay 14567004',                                 '2026-09-25 10:21:00'),
(NULL, 'PAYMENT_SUCCESS',  'ORDER',  5, 50000,  'VNPay 14567005',                                 '2026-09-25 11:46:00'),
(NULL, 'PAYMENT_SUCCESS',  'ORDER',  6, 60000,  'VNPay 14567006',                                 '2026-09-25 12:11:00'),
(NULL, 'PAYMENT_SUCCESS',  'ORDER',  7, 45000,  'VNPay 14567007',                                 '2026-09-25 13:01:00'),
(3,    'ORDER_CANCELLED',  'ORDER',  7, 45000,  'Khách tại quầy đã mua hết',                      '2026-09-25 13:20:00'),
(3,    'REFUND_REQUESTED', 'REFUND', 1, 45000,  'RF-FR260925-0007, lý do STORE_CANCELLED',        '2026-09-25 13:20:00');
