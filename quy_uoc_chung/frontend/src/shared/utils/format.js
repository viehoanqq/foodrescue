// ============================================================================
//  Định dạng hiển thị dùng chung. Không tự format tiền / ngày / khoảng cách ở chỗ khác.
//  Backend trả: tiền là số nguyên (VNĐ), thời gian dạng "2026-09-25T21:00:00" (giờ Việt Nam).
// ============================================================================

const moneyFmt = new Intl.NumberFormat('vi-VN');

/** 45000 -> "45.000đ" */
export function formatMoney(value) {
  if (value === null || value === undefined || value === '') return '-';
  return `${moneyFmt.format(Number(value))}đ`;
}

/** Phần trăm giảm: (80000, 30000) -> 63 */
export function discountPercent(originalPrice, rescuePrice) {
  if (!originalPrice) return 0;
  return Math.round((1 - Number(rescuePrice) / Number(originalPrice)) * 100);
}

const pad = (n) => String(n).padStart(2, '0');

/** Chuỗi ISO không có múi giờ -> Date theo giờ máy (máy người dùng ở Việt Nam). */
function toDate(iso) {
  return iso instanceof Date ? iso : new Date(iso);
}

/** "2026-09-25T21:00:00" -> "21:00" */
export function formatTime(iso) {
  if (!iso) return '-';
  const d = toDate(iso);
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** "2026-09-25" hoặc ISO đầy đủ -> "25/09/2026" */
export function formatDate(iso) {
  if (!iso) return '-';
  const d = toDate(iso.length === 10 ? `${iso}T00:00:00` : iso);
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`;
}

/** "2026-09-25T21:00:00" -> "25/09/2026 21:00" */
export function formatDateTime(iso) {
  if (!iso) return '-';
  return `${formatDate(iso)} ${formatTime(iso)}`;
}

/** Khung giờ lấy hàng: "21:00 - 22:00", thêm ngày nếu không phải hôm nay: "21:00 - 22:00, 26/09" */
export function formatPickupWindow(startIso, endIso, now = new Date()) {
  const s = toDate(startIso);
  const sameDay = s.toDateString() === now.toDateString();
  const range = `${formatTime(startIso)} - ${formatTime(endIso)}`;
  return sameDay ? range : `${range}, ${pad(s.getDate())}/${pad(s.getMonth() + 1)}`;
}

/** 850 -> "850 m", 2350 -> "2,4 km"; estimated=true thêm dấu "~" */
export function formatDistance(meters, estimated = false) {
  if (meters === null || meters === undefined) return '';
  const text = meters < 1000
    ? `${Math.round(meters / 10) * 10} m`
    : `${(meters / 1000).toLocaleString('vi-VN', { maximumFractionDigits: 1 })} km`;
  return estimated ? `~${text}` : text;
}

/** 452 giây -> "8 phút" */
export function formatDuration(seconds) {
  if (seconds === null || seconds === undefined) return '';
  return `${Math.max(1, Math.round(seconds / 60))} phút`;
}

/** Dòng khoảng cách trên thẻ túi: "2,4 km · 8 phút" hoặc "~2,4 km" */
export function formatDistanceLine(meters, seconds, estimated = false) {
  const d = formatDistance(meters, estimated);
  return estimated || seconds === null || seconds === undefined ? d : `${d} · ${formatDuration(seconds)}`;
}
