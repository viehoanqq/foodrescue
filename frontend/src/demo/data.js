const KEY = 'foodrescue_demo_v2';
const initial = {
  stores: [
    { id: 1, ownerId: 2, name: 'Bánh mì An Phát', owner: 'owner@gmail.com', address: '105 An Dương Vương, Quận 5', status: 'APPROVED', commissionRate: 15, rating: 4.8 },
    { id: 2, ownerId: 3, name: 'Sweet Paris Bakery', owner: 'sweetparis@gmail.com', address: '45 Lê Duẩn, Quận 1', status: 'APPROVED', commissionRate: 15, rating: 4.7 },
    { id: 3, ownerId: 4, name: 'Bếp Nhà Xanh', owner: 'bepxanh@gmail.com', address: '12 Nguyễn Thị Minh Khai, Quận 1', status: 'PENDING', commissionRate: 15, rating: 0 },
  ],
  categories: [
    { id: 1, name: 'Bánh mì' }, { id: 2, name: 'Bánh ngọt' }, { id: 3, name: 'Cơm và món chính' },
  ],
  batches: [
    { id: 1, storeId: 1, categoryId: 1, title: 'Túi bánh mì cuối ngày', originalPrice: 80000, rescuePrice: 30000, quantity: 9, remaining: 8, status: 'AVAILABLE', pickup: '20:00–21:30' },
    { id: 2, storeId: 2, categoryId: 2, title: 'Hộp bánh ngọt bất ngờ', originalPrice: 120000, rescuePrice: 45000, quantity: 7, remaining: 6, status: 'AVAILABLE', pickup: '20:30–22:00' },
    { id: 3, storeId: 1, categoryId: 3, title: 'Bữa tối tiết kiệm', originalPrice: 90000, rescuePrice: 39000, quantity: 4, remaining: 4, status: 'AVAILABLE', pickup: '18:30–20:00' },
  ],
  cart: [],
  orders: [
    { id: 101, customer: 'Khách mẫu', storeId: 1, items: [{ batchId: 1, quantity: 1, price: 30000 }], total: 30000, status: 'CONFIRMED', paymentStatus: 'PAID', pickupCode: 'FR101', createdAt: '22/11/2026 10:15' },
    { id: 102, customer: 'Khách mẫu', storeId: 2, items: [{ batchId: 2, quantity: 1, price: 45000 }], total: 45000, status: 'COMPLETED', paymentStatus: 'PAID', pickupCode: 'FR102', createdAt: '21/11/2026 17:30' },
    { id: 103, customer: 'Khách mẫu', storeId: 1, items: [{ batchId: 3, quantity: 1, price: 39000 }], total: 39000, status: 'CANCELLED', paymentStatus: 'REFUND_PENDING', pickupCode: 'FR103', createdAt: '20/11/2026 18:00' },
  ],
  refunds: [{ id: 1, orderId: 103, amount: 39000, status: 'PENDING', reason: 'STORE_CANCELLED' }],
  payouts: [{ id: 1, storeId: 1, amount: 25500, status: 'PENDING', period: '01–15/11/2026' }],
  reviews: [{ id: 1, storeId: 2, orderId: 102, rating: 5, comment: 'Bánh ngon, nhận hàng nhanh' }],
  audit: [{ id: 1, action: 'DEMO_INITIALIZED', actor: 'Hệ thống', time: '22/11/2026' }],
};

export function readDemo() {
  try { return JSON.parse(localStorage.getItem(KEY)) || structuredClone(initial); }
  catch { return structuredClone(initial); }
}
export function writeDemo(next) {
  localStorage.setItem(KEY, JSON.stringify(next));
  window.dispatchEvent(new Event('foodrescue:demo-change'));
}
export function updateDemo(change) {
  const next = structuredClone(readDemo());
  change(next);
  writeDemo(next);
  return next;
}
export function resetDemo() { writeDemo(structuredClone(initial)); }
export const money = value => `${Number(value || 0).toLocaleString('vi-VN')} đ`;
