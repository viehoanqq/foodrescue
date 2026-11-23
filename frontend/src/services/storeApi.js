import { api } from './axios';

export const storeApi = {
  registration: () => api.get('/store/registration'),
  register: (values, license, image) => {
    const form = new FormData();
    form.append('data', JSON.stringify(values));
    form.append('license', license);
    if (image) form.append('image', image);
    return api.post('/store/registration', form);
  },
  stores: (params) => api.get('/stores', { params }),
  store: (id) => api.get(`/stores/${id}`),
  reviews: (id, params) => api.get(`/stores/${id}/reviews`, { params }),
  createReview: (id, values) => api.post(`/stores/${id}/reviews`, values),
  profile: () => api.get('/store/profile'),
  updateProfile: (values) => api.put('/store/profile', values),
  staff: (params) => api.get('/store/staff', { params }),
  createStaff: (values) => api.post('/store/staff', values),
  setStaffLocked: (id, locked) => api.post(`/store/staff/${id}/${locked ? 'lock' : 'unlock'}`),
  adminStores: (params) => api.get('/admin/stores', { params }),
  adminStore: (id) => api.get(`/admin/stores/${id}`),
  updateAdminStore: (id, values) => api.put(`/admin/stores/${id}`, values),
  adminLicense: (id) => api.get(`/admin/stores/${id}/license`, { responseType: 'blob' }),
  approve: (id, commissionRate) => api.post(`/admin/stores/${id}/approve`, { commissionRate }),
  reject: (id, reason) => api.post(`/admin/stores/${id}/reject`, { reason }),
  setStoreLocked: (id, locked, reason) => api.post(`/admin/stores/${id}/${locked ? 'lock' : 'unlock'}`, locked ? { reason } : undefined),
  setCommission: (id, commissionRate) => api.put(`/admin/stores/${id}/commission`, { commissionRate }),
};
