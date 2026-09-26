// ============================================================================
//  Nhãn tiếng Việt + màu Tag cho mọi trạng thái. Giá trị khoá TRÙNG ĐÚNG enum backend
//  (com.foodrescue.common.enums) và cột trạng thái trong CSDL.
//  Dùng: <StatusTag type="order" value={order.status} />  (không tự viết nhãn ở chỗ khác)
//  color là tên màu preset của Ant Design Tag.
// ============================================================================

export const ORDER_STATUS = {
  PENDING_PAYMENT: { label: 'Chờ thanh toán', color: 'gold' },
  CONFIRMED: { label: 'Chờ nhận hàng', color: 'blue' },
  COMPLETED: { label: 'Đã nhận hàng', color: 'green' },
  CANCELLED: { label: 'Đã huỷ', color: 'red' },
  EXPIRED: { label: 'Hết hạn thanh toán', color: 'default' },
  NO_SHOW: { label: 'Không đến lấy', color: 'volcano' },
};

export const PAYMENT_STATUS = {
  UNPAID: { label: 'Chưa thanh toán', color: 'default' },
  PAID: { label: 'Đã thanh toán', color: 'green' },
  REFUND_PENDING: { label: 'Đang hoàn tiền', color: 'orange' },
  REFUNDED: { label: 'Đã hoàn tiền', color: 'purple' },
};

export const BATCH_STATUS = {
  AVAILABLE: { label: 'Đang bán', color: 'green' },
  SOLD_OUT: { label: 'Hết hàng', color: 'volcano' },
  EXPIRED: { label: 'Hết giờ', color: 'default' },
  CANCELLED: { label: 'Đã huỷ', color: 'red' },
};

export const STORE_STATUS = {
  PENDING: { label: 'Chờ duyệt', color: 'gold' },
  APPROVED: { label: 'Đang hoạt động', color: 'green' },
  REJECTED: { label: 'Bị từ chối', color: 'red' },
  LOCKED: { label: 'Đã khoá', color: 'default' },
};

export const USER_STATUS = {
  ACTIVE: { label: 'Hoạt động', color: 'green' },
  LOCKED: { label: 'Đã khoá', color: 'default' },
};

export const ROLE = {
  ADMIN: { label: 'Quản trị viên', color: 'purple' },
  STORE_OWNER: { label: 'Chủ cửa hàng', color: 'orange' },
  STORE_STAFF: { label: 'Nhân viên', color: 'blue' },
  CUSTOMER: { label: 'Khách hàng', color: 'default' },
};

export const REFUND_STATUS = {
  PENDING: { label: 'Chờ hoàn', color: 'orange' },
  COMPLETED: { label: 'Đã hoàn', color: 'green' },
};

export const REFUND_REASON = {
  STORE_CANCELLED: { label: 'Cửa hàng huỷ đơn', color: 'default' },
  BATCH_CANCELLED: { label: 'Cửa hàng huỷ túi', color: 'default' },
  ADMIN_CANCELLED: { label: 'Admin huỷ đơn', color: 'default' },
  LATE_PAYMENT: { label: 'Thanh toán về muộn', color: 'default' },
};

export const PAYOUT_STATUS = {
  PENDING: { label: 'Chờ chuyển khoản', color: 'gold' },
  PAID: { label: 'Đã chi', color: 'green' },
  CANCELLED: { label: 'Đã huỷ', color: 'default' },
};

export const STOCK_REASON = {
  LISTED: { label: 'Đăng túi', color: 'blue' },
  ADDED: { label: 'Thêm túi', color: 'green' },
  REMOVED: { label: 'Bớt túi', color: 'volcano' },
  ORDERED: { label: 'Khách đặt', color: 'orange' },
  RETURNED: { label: 'Trả lại', color: 'cyan' },
};

const MAP = {
  order: ORDER_STATUS,
  payment: PAYMENT_STATUS,
  batch: BATCH_STATUS,
  store: STORE_STATUS,
  user: USER_STATUS,
  role: ROLE,
  refund: REFUND_STATUS,
  refundReason: REFUND_REASON,
  payout: PAYOUT_STATUS,
  stock: STOCK_REASON,
};

/** Lấy { label, color } cho 1 giá trị; giá trị lạ thì hiện nguyên văn để dễ phát hiện lỗi. */
export function statusInfo(type, value) {
  return MAP[type]?.[value] ?? { label: value ?? '-', color: 'default' };
}
