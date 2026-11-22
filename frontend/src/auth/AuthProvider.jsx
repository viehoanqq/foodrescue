import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { identityApi } from '../services/identityApi';
import { TOKEN_KEY } from '../services/axios';

const AuthContext = createContext(null);

function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem('fr_user');
  localStorage.removeItem('fr_dev_user_id');
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(Boolean(localStorage.getItem(TOKEN_KEY)));
  const [error, setError] = useState(null);
  const checking = useRef(false);

  const refresh = useCallback(async (silent = false) => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) {
      setUser(null);
      setLoading(false);
      return;
    }
    if (checking.current) return;
    checking.current = true;
    if (!silent) {
      setLoading(true);
      setError(null);
    }
    try {
      const current = await identityApi.me();
      if (localStorage.getItem(TOKEN_KEY) === token) setUser(current);
    } catch (failure) {
      if (localStorage.getItem(TOKEN_KEY) !== token) return;
      if (failure.status === 401 || failure.code === 'ACCOUNT_LOCKED') {
        clearSession();
        setUser(null);
      } else if (!silent) {
        setError(failure.message || 'Không tải được tài khoản');
      }
    } finally {
      checking.current = false;
      if (!silent) setLoading(false);
    }
  }, []);

  useEffect(() => { refresh(); }, [refresh]);
  useEffect(() => {
    const checkSession = () => { refresh(true); };
    const passwordRequired = () => setUser((previous) => previous ? { ...previous, mustChangePassword: true } : previous);
    window.addEventListener('foodrescue:session-check', checkSession);
    window.addEventListener('foodrescue:password-change-required', passwordRequired);
    return () => {
      window.removeEventListener('foodrescue:session-check', checkSession);
      window.removeEventListener('foodrescue:password-change-required', passwordRequired);
    };
  }, [refresh]);

  const authenticate = useCallback(async (credentials, mode, allowedRoles) => {
    const result = mode === 'register' ? await identityApi.register(credentials) : await identityApi.login(credentials);
    if (allowedRoles && !allowedRoles.includes(result.user.role)) {
      const roleError = new Error('Tài khoản này không có quyền vào khu vực đã chọn.');
      roleError.status = 403;
      roleError.code = 'ROLE_MISMATCH';
      throw roleError;
    }
    localStorage.setItem(TOKEN_KEY, result.token);
    localStorage.removeItem('fr_dev_user_id');
    setUser(result.user);
    setError(null);
    return result.user;
  }, []);

  const logout = useCallback(() => { clearSession(); setUser(null); setError(null); }, []);
  const changePassword = useCallback(async (details) => {
    await identityApi.changePassword(details);
    clearSession();
    setUser(null);
  }, []);

  const updateProfile = useCallback(async (details) => {
    const updated = await identityApi.updateProfile(details);
    setUser(updated);
    return updated;
  }, []);

  const value = useMemo(() => ({ user, loading, error, refresh, authenticate, logout, changePassword, updateProfile }),
    [user, loading, error, refresh, authenticate, logout, changePassword, updateProfile]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth requires AuthProvider');
  return context;
}
