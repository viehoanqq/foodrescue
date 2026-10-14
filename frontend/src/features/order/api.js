import { api } from '../../shared/api/axios';

// ---- Giỏ hàng ----
export const cartApi = {
  getCart: () => api.get('/cart'),
  addToCart: (batchId, quantity = 1) => api.post('/cart', { batchId, quantity }),
  updateQuantity: (batchId, quantity) => api.put(`/cart/${batchId}`, { quantity }),
  removeItem: (batchId) => api.delete(`/cart/${batchId}`),
  clearCart: () => api.delete('/cart'),
};

// ---- Đơn hàng phía khách hàng ----
export const orderApi = {
  createOrder: (data) => api.post('/orders', data),
  getMyOrders: (status) => api.get('/orders', { params: status ? { status } : {} }),
  getOrderDetail: (id) => api.get(`/orders/${id}`),
  cancelOrder: (id, reason) => api.post(`/orders/${id}/cancel`, { reason }),
};

// ---- Thanh toán VNPay Sandbox ----
export const paymentApi = {
  createPaymentUrl: (orderId) => api.post('/payments/create-url', { orderId }),
  verifyPaymentReturn: (params) => api.get('/payments/vnpay/return', { params }),
  mockSuccess: (orderId) => api.post('/payments/mock-success', { orderId }),
};

// ---- Màn hình quầy, giao hàng bằng mã ----
export const pickupApi = {
  lookup: (code) => api.get('/store/pickup/lookup', { params: { code } }),
  handover: (pickupCode) => api.post('/store/pickup/handover', { pickupCode }),
};

// ---- Đơn hàng phía cửa hàng ----
export const storeOrderApi = {
  getStoreOrders: (status) => api.get('/store/orders', { params: status ? { status } : {} }),
  getStoreOrderDetail: (id) => api.get(`/store/orders/${id}`),
  cancelOrder: (id, reason) => api.post(`/store/orders/${id}/cancel`, { reason }),
};

// ---- Túi cứu thực phẩm (Chi tiết & Đánh giá) ----
export const batchApi = {
  getBatchDetail: (id) => api.get(`/batches/${id}`),
};
