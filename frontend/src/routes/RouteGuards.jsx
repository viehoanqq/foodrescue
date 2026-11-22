import { Button, Result, Spin } from 'antd';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { homeForRole } from './paths';

function SessionState({ loading, error, retry }) {
  if (loading) return <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}><Spin size="large" /></div>;
  if (error) return <Result status="warning" title="Không tải được tài khoản" subTitle={error}
    extra={<Button type="primary" onClick={() => retry()}>Thử lại</Button>} />;
  return null;
}

export function ProtectedRoute() {
  const { user, loading, error, refresh } = useAuth();
  const location = useLocation();
  const state = SessionState({ loading, error, retry: refresh });
  if (state) return state;
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />;
  if (user.mustChangePassword) return <Navigate to="/change-password" replace />;
  return <Outlet />;
}

export function RoleRoute({ roles }) {
  const { user } = useAuth();
  if (!user || !roles.includes(user.role)) return <Navigate to={homeForRole(user?.role)} replace />;
  return <Outlet />;
}

export function AnonymousRoute() {
  const { user, loading, error, refresh } = useAuth();
  const state = SessionState({ loading, error, retry: refresh });
  if (state) return state;
  if (user) return <Navigate to={user.mustChangePassword ? '/change-password' : homeForRole(user.role)} replace />;
  return <Outlet />;
}

export function PasswordChangeRoute() {
  const { user, loading, error, refresh } = useAuth();
  const state = SessionState({ loading, error, retry: refresh });
  if (state) return state;
  if (!user) return <Navigate to="/login" replace />;
  if (!user.mustChangePassword) return <Navigate to={homeForRole(user.role)} replace />;
  return <Outlet />;
}
