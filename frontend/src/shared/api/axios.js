// ============================================================================
//  Axios dùng chung cho MỌI lời gọi API. Mỗi module viết file api riêng
//  (vd src/features/order/api.js) nhưng luôn import `api` từ đây.
// ============================================================================
import axios from 'axios';

export const TOKEN_KEY = 'fr_token';
export const USER_KEY = 'fr_user';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  timeout: 15000,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  const devUserId = localStorage.getItem('fr_dev_user_id');
  if (devUserId) config.headers['X-Dev-User-Id'] = devUserId;
  return config;
});

/**
 * Mọi lỗi được chuẩn hoá thành { status, code, message, details } theo ApiError của backend.
 * Component chỉ cần: catch (e) { message.error(e.message) } hoặc kiểm tra e.code.
 */
api.interceptors.response.use(
  (res) => res.data,
  (error) => {
    const status = error.response?.status ?? 0;
    const body = error.response?.data ?? {};
    const normalized = {
      status,
      code: body.code ?? (status === 0 ? 'NETWORK_ERROR' : 'UNKNOWN_ERROR'),
      message: body.message ?? (status === 0 ? 'Không kết nối được máy chủ' : 'Có lỗi xảy ra, vui lòng thử lại'),
      details: body.details ?? [],
    };
    if (status === 401) {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
      if (!window.location.pathname.startsWith('/login')) window.location.assign('/login');
    } else if (normalized.code === 'PASSWORD_CHANGE_REQUIRED' && window.location.pathname !== '/change-password') {
      window.location.assign('/change-password');
    }
    return Promise.reject(normalized);
  },
);

/** Chuyển details [{field, message}] thành lỗi cho Ant Design Form: form.setFields(toFormErrors(e)) */
export function toFormErrors(err) {
  return (err.details ?? []).map((d) => ({ name: d.field, errors: [d.message] }));
}
