import { api } from './axios';

export const identityApi = {
  login: (credentials) => api.post('/auth/login', credentials),
  register: (details) => api.post('/auth/register', details),
  sendRegistrationOtp: (email) => api.post('/auth/register/otp', { email }),
  googleLogin: (idToken) => api.post('/auth/google', { idToken }),
  linkGoogle: (idToken, password) => api.post('/me/google', { idToken, password }),
  me: () => api.get('/me'),
  updateProfile: (details) => api.put('/me', details),
  changePassword: (details) => api.put('/me/password', details),
  users: (params) => api.get('/admin/users', { params }),
  auditLogs: (params) => api.get('/admin/audit-logs', { params }),
  setUserLocked: (id, locked) => api.post(`/admin/users/${id}/${locked ? 'lock' : 'unlock'}`),
  resetPassword: (id) => api.post(`/admin/users/${id}/reset-password`),
};
