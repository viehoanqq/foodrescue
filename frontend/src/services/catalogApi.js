import { api } from './axios';

export const catalogApi = {
  storeCategories: () => api.get('/store/categories'),
  publicCategories: (storeId) => api.get('/categories', { params: { storeId } }),
  createCategory: (values) => api.post('/store/categories', values),
  updateCategory: (id, values) => api.put(`/store/categories/${id}`, values),
  archiveCategory: (id) => api.delete(`/store/categories/${id}`),
  adminCategories: (storeId) => api.get('/admin/categories', { params: { storeId } }),
  createAdminCategory: (storeId, values) => api.post('/admin/categories', values, { params: { storeId } }),
  updateAdminCategory: (storeId, id, values) => api.put(`/admin/categories/${id}`, values, { params: { storeId } }),
  archiveAdminCategory: (storeId, id) => api.delete(`/admin/categories/${id}`, { params: { storeId } }),
  storeBatches: () => api.get('/store/batches'),
  createBatch: (values) => api.post('/store/batches', values),
  updateBatch: (id, values) => api.put(`/store/batches/${id}`, values),
  adjustStock: (id, values) => api.patch(`/store/batches/${id}/quantity`, values),
  stockMovements: (id, params) => api.get(`/store/batches/${id}/stock-movements`, { params }),
  cancelBatch: (id, reason) => api.post(`/store/batches/${id}/cancel`, { reason }),
  adminBatches: (params) => api.get('/admin/batches', { params }),
  cancelAdminBatch: (id, reason) => api.post(`/admin/batches/${id}/cancel`, { reason }),
};
