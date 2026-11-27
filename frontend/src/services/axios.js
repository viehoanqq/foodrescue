import axios from 'axios';

export const TOKEN_KEY = 'fr_token';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  timeout: 15000,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const status = error.response?.status ?? 0;
    const body = error.response?.data ?? {};
    const normalized = {
      status,
      code: body.code ?? (status === 0 ? 'NETWORK_ERROR' : 'UNKNOWN_ERROR'),
      message: body.message ?? (status === 0 ? 'Không kết nối được máy chủ' : 'Có lỗi xảy ra, vui lòng thử lại'),
      details: body.details ?? [],
    };
    const path = error.config?.url ?? '';
    if (status === 401 && !['/auth/login', '/auth/register', '/auth/google', '/me'].includes(path)) {
      window.dispatchEvent(new Event('foodrescue:session-check'));
    }
    if (normalized.code === 'PASSWORD_CHANGE_REQUIRED') {
      window.dispatchEvent(new Event('foodrescue:password-change-required'));
    }
    return Promise.reject(normalized);
  },
);

export function toFormErrors(error) {
  return (error.details ?? []).map((detail) => ({ name: detail.field, errors: [detail.message] }));
}
