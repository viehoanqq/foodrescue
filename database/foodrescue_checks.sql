-- =====================================================================
--  FOODRESCUE - Kiểm tra tính đúng của dữ liệu (chạy sau khi test / trước khi demo)
--  Mỗi câu kiểm tra trả về các dòng VI PHẠM. Dữ liệu đúng => mọi câu C1..C12 trả về 0 dòng,
--  câu C0 (đối soát tổng) phải có chenh_lech = 0, câu C13 chỉ để xem số liệu từng cửa hàng.
--  Nhóm: tiền (C0, C4-C9, C12) · kho hàng (C1, C1b, C1c, C2) · đơn và giỏ hàng (C3-C3d, C5, C11) · đánh giá (C10)
-- =====================================================================
USE foodrescuedb;

-- C0. Đối soát tổng: tiền thu từ khách = hoàn + chờ hoàn + đã chi + chờ chi + nợ cửa hàng + phí + đơn chưa giao
SELECT t.*, tien_thu - (da_hoan + cho_hoan + da_chi + cho_chi + no_cua_hang + phi_nen_tang + don_chua_giao) AS chenh_lech
FROM (
  SELECT
    (SELECT COALESCE(SUM(amount), 0)            FROM payments WHERE status = 'SUCCESS')                              AS tien_thu,
    (SELECT COALESCE(SUM(amount), 0)            FROM refunds  WHERE status = 'COMPLETED')                            AS da_hoan,
    (SELECT COALESCE(SUM(amount), 0)            FROM refunds  WHERE status = 'PENDING')                              AS cho_hoan,
    (SELECT COALESCE(SUM(net_amount), 0)        FROM payouts  WHERE status = 'PAID')                                 AS da_chi,
    (SELECT COALESCE(SUM(net_amount), 0)        FROM payouts  WHERE status = 'PENDING')                              AS cho_chi,
    (SELECT COALESCE(SUM(store_earning), 0)     FROM orders   WHERE status IN ('COMPLETED','NO_SHOW') AND payout_id IS NULL) AS no_cua_hang,
    (SELECT COALESCE(SUM(commission_amount), 0) FROM orders   WHERE status IN ('COMPLETED','NO_SHOW'))               AS phi_nen_tang,
    (SELECT COALESCE(SUM(total_amount), 0)      FROM orders   WHERE status = 'CONFIRMED')                            AS don_chua_giao
) t;

-- C1. Tồn kho: quantity - remaining phải bằng tổng số túi trong các đơn còn hiệu lực
SELECT b.id, b.quantity, b.remaining_quantity,
       COALESCE(SUM(CASE WHEN o.status IN ('PENDING_PAYMENT','CONFIRMED','COMPLETED','NO_SHOW') THEN i.quantity END), 0) AS da_ban
FROM rescue_batches b
LEFT JOIN order_items i ON i.batch_id = b.id
LEFT JOIN orders o      ON o.id = i.order_id
GROUP BY b.id
HAVING b.quantity - b.remaining_quantity <> da_ban;

-- C1b. Sổ kho khớp tồn kho: tổng mọi biến động = remaining; tổng LISTED + ADDED + REMOVED = quantity;
--      dòng cuối cùng có remaining_after = remaining
SELECT b.id, b.quantity, b.remaining_quantity,
       COALESCE(SUM(m.change_qty), 0) AS tong_bien_dong,
       COALESCE(SUM(CASE WHEN m.reason IN ('LISTED','ADDED','REMOVED') THEN m.change_qty END), 0) AS tong_dua_len,
       (SELECT m2.remaining_after FROM stock_movements m2 WHERE m2.batch_id = b.id ORDER BY m2.created_at DESC, m2.id DESC LIMIT 1) AS remaining_cuoi
FROM rescue_batches b LEFT JOIN stock_movements m ON m.batch_id = b.id
GROUP BY b.id
HAVING tong_bien_dong <> b.remaining_quantity OR tong_dua_len <> b.quantity OR remaining_cuoi <> b.remaining_quantity;

-- C1c. Sổ kho khớp đơn: mỗi túi trong đơn có đúng 1 dòng ORDERED cùng số lượng; đơn đã EXPIRED/CANCELLED có dòng RETURNED
SELECT * FROM (
  SELECT i.order_id, i.batch_id, i.quantity, o.status,
         (SELECT -SUM(change_qty) FROM stock_movements m WHERE m.order_id = i.order_id AND m.batch_id = i.batch_id AND m.reason = 'ORDERED')  AS da_tru,
         (SELECT  SUM(change_qty) FROM stock_movements m WHERE m.order_id = i.order_id AND m.batch_id = i.batch_id AND m.reason = 'RETURNED') AS da_tra
  FROM order_items i JOIN orders o ON o.id = i.order_id
) x
WHERE da_tru IS NULL OR da_tru <> quantity
   OR (status IN ('EXPIRED','CANCELLED') AND (da_tra IS NULL OR da_tra <> quantity))
   OR (status NOT IN ('EXPIRED','CANCELLED') AND da_tra IS NOT NULL);

-- C2. Túi hết hàng phải ở trạng thái SOLD_OUT (hoặc đã hết hạn / huỷ)
SELECT id, remaining_quantity, status FROM rescue_batches
WHERE (remaining_quantity = 0 AND status = 'AVAILABLE') OR (remaining_quantity > 0 AND status = 'SOLD_OUT');

-- C3. Mọi túi trong đơn phải thuộc cửa hàng của đơn
SELECT o.id, o.store_id, b.id AS batch_id, b.store_id AS batch_store
FROM orders o JOIN order_items i ON i.order_id = o.id JOIN rescue_batches b ON b.id = i.batch_id
WHERE o.store_id <> b.store_id;

-- C3b. Tổng đơn = tổng chi tiết; số túi = tổng số lượng chi tiết; đơn nào cũng có ít nhất 1 dòng chi tiết
SELECT o.id, o.total_amount, SUM(i.subtotal) AS tong_chi_tiet, o.item_count, SUM(i.quantity) AS so_tui
FROM orders o LEFT JOIN order_items i ON i.order_id = o.id
GROUP BY o.id
HAVING COUNT(i.id) = 0 OR o.total_amount <> SUM(i.subtotal) OR o.item_count <> SUM(i.quantity);

-- C3c. Khung giờ lấy của đơn phải nằm trong khung giờ của mọi túi trong đơn
SELECT o.id, o.pickup_start, o.pickup_end, b.id AS batch_id, b.pickup_start AS b_start, b.pickup_end AS b_end
FROM orders o JOIN order_items i ON i.order_id = o.id JOIN rescue_batches b ON b.id = i.batch_id
WHERE o.pickup_start < b.pickup_start OR o.pickup_end > b.pickup_end;

-- C3d. Giỏ hàng: mỗi khách chỉ chứa túi của 1 cửa hàng
SELECT c.customer_id, COUNT(DISTINCT b.store_id) AS so_cua_hang
FROM cart_items c JOIN rescue_batches b ON b.id = c.batch_id
GROUP BY c.customer_id
HAVING COUNT(DISTINCT b.store_id) > 1;

-- C4. Đơn đã trả tiền (PAID / REFUND_*) phải có đúng 1 giao dịch SUCCESS bằng đúng số tiền; đơn UNPAID thì không có
SELECT o.id, o.payment_status, o.total_amount, COUNT(p.id) AS so_gd_thanh_cong, COALESCE(SUM(p.amount), 0) AS tong_gd
FROM orders o LEFT JOIN payments p ON p.order_id = o.id AND p.status = 'SUCCESS'
GROUP BY o.id
HAVING (o.payment_status <> 'UNPAID' AND (COUNT(p.id) <> 1 OR SUM(p.amount) <> o.total_amount))
    OR (o.payment_status = 'UNPAID' AND COUNT(p.id) > 0);

-- C5. Trạng thái thanh toán phải khớp trạng thái đơn
SELECT id, status, payment_status FROM orders
WHERE (status IN ('CONFIRMED','COMPLETED','NO_SHOW') AND payment_status <> 'PAID')
   OR (status IN ('PENDING_PAYMENT','EXPIRED')        AND payment_status <> 'UNPAID')
   OR (status = 'CANCELLED' AND payment_status = 'PAID');

-- C6. Đơn REFUND_PENDING / REFUNDED phải có đúng 1 bản ghi hoàn tiền tương ứng, số tiền bằng total_amount
SELECT o.id, o.payment_status, r.status AS refund_status, o.total_amount, r.amount
FROM orders o LEFT JOIN refunds r ON r.order_id = o.id
WHERE (o.payment_status = 'REFUND_PENDING' AND (r.id IS NULL OR r.status <> 'PENDING'   OR r.amount <> o.total_amount))
   OR (o.payment_status = 'REFUNDED'       AND (r.id IS NULL OR r.status <> 'COMPLETED' OR r.amount <> o.total_amount))
   OR (o.payment_status IN ('UNPAID','PAID') AND r.id IS NOT NULL);

-- C7. Kỳ chi trả: số đơn và số tiền phải bằng tổng của đúng các đơn gắn vào kỳ; đơn phải cùng cửa hàng
SELECT p.id, p.order_count, COUNT(o.id) AS so_don, p.gross_amount, SUM(o.total_amount) AS gross,
       p.commission_amount, SUM(o.commission_amount) AS phi, p.net_amount, SUM(o.store_earning) AS net
FROM payouts p LEFT JOIN orders o ON o.payout_id = p.id
WHERE p.status <> 'CANCELLED'
GROUP BY p.id
HAVING p.order_count <> COUNT(o.id) OR p.gross_amount <> SUM(o.total_amount)
    OR p.commission_amount <> SUM(o.commission_amount) OR p.net_amount <> SUM(o.store_earning)
    OR SUM(o.store_id <> p.store_id) > 0;

-- C8. Kỳ chi trả đã huỷ không được còn đơn gắn vào
SELECT p.id, COUNT(o.id) FROM payouts p JOIN orders o ON o.payout_id = p.id WHERE p.status = 'CANCELLED' GROUP BY p.id;

-- C9. Đơn trong kỳ chi trả phải có ngày chốt đơn nằm trong kỳ
SELECT o.id, o.completed_at, p.period_from, p.period_to FROM orders o JOIN payouts p ON p.id = o.payout_id
WHERE DATE(o.completed_at) NOT BETWEEN p.period_from AND p.period_to;

-- C10. Đánh giá phải thuộc đơn COMPLETED, đúng khách và đúng cửa hàng của đơn
SELECT r.id FROM reviews r JOIN orders o ON o.id = r.order_id
WHERE o.status <> 'COMPLETED' OR r.customer_id <> o.customer_id OR r.store_id <> o.store_id;

-- C11. Người giao hàng phải là chủ hoặc nhân viên của đúng cửa hàng
SELECT o.id, o.handled_by FROM orders o
JOIN users u ON u.id = o.handled_by
JOIN stores s ON s.id = o.store_id
WHERE NOT ((u.role = 'STORE_OWNER' AND s.owner_id = u.id) OR (u.role = 'STORE_STAFF' AND u.store_id = s.id));

-- C12. Mọi giao dịch tiền quan trọng phải có dòng nhật ký
SELECT 'PAYMENT_SUCCESS' AS thieu_log, p.order_id AS id FROM payments p
 WHERE p.status = 'SUCCESS'
   AND NOT EXISTS (SELECT 1 FROM audit_logs a WHERE a.action = 'PAYMENT_SUCCESS' AND a.entity_type = 'ORDER' AND a.entity_id = p.order_id)
UNION ALL
SELECT 'REFUND_REQUESTED', r.id FROM refunds r
 WHERE NOT EXISTS (SELECT 1 FROM audit_logs a WHERE a.action = 'REFUND_REQUESTED' AND a.entity_type = 'REFUND' AND a.entity_id = r.id)
UNION ALL
SELECT 'PAYOUT_PAID', p.id FROM payouts p
 WHERE p.status = 'PAID'
   AND NOT EXISTS (SELECT 1 FROM audit_logs a WHERE a.action = 'PAYOUT_PAID' AND a.entity_type = 'PAYOUT' AND a.entity_id = p.id);

-- C13. Điểm đánh giá và doanh thu từng cửa hàng (xem, không phải kiểm tra lỗi)
SELECT s.id, s.name,
       (SELECT ROUND(AVG(rating), 2) FROM reviews r WHERE r.store_id = s.id)                                          AS diem_tb,
       (SELECT COALESCE(SUM(total_amount), 0)      FROM orders o WHERE o.store_id = s.id AND o.status IN ('COMPLETED','NO_SHOW')) AS doanh_thu,
       (SELECT COALESCE(SUM(commission_amount), 0) FROM orders o WHERE o.store_id = s.id AND o.status IN ('COMPLETED','NO_SHOW')) AS phi,
       (SELECT COALESCE(SUM(store_earning), 0)     FROM orders o WHERE o.store_id = s.id AND o.status IN ('COMPLETED','NO_SHOW')) AS thuc_nhan,
       (SELECT COALESCE(SUM(net_amount), 0)        FROM payouts p WHERE p.store_id = s.id AND p.status = 'PAID')                   AS da_chi,
       (SELECT COALESCE(SUM(store_earning), 0)     FROM orders o WHERE o.store_id = s.id AND o.status IN ('COMPLETED','NO_SHOW') AND o.payout_id IS NULL) AS con_no
FROM stores s;
