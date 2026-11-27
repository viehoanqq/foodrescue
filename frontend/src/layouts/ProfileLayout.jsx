import { Menu, Typography } from 'antd';
import { EnvironmentOutlined, InboxOutlined, LockOutlined, ShopOutlined, UserOutlined } from '@ant-design/icons';
import { useAuth } from '../auth/AuthProvider';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import './layout.css';

export default function ProfileLayout() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const location = useLocation();
  const selected = location.pathname.startsWith('/me/orders')
    ? '/me/orders'
    : location.pathname === '/me/address'
      ? '/me/address'
      : location.pathname === '/me/password'
        ? '/me/password'
        : location.pathname === '/me/store'
          ? '/me/store'
          : '/me';

  const visibleItems = [
    { key: '/me', icon: <UserOutlined />, label: 'Hồ sơ cá nhân' },
    ...(user.role === 'CUSTOMER' ? [{ key: '/me/address', icon: <EnvironmentOutlined />, label: 'Địa chỉ của tôi' }] : []),
    { key: '/me/password', icon: <LockOutlined />, label: 'Đổi mật khẩu' },
    ...(user.role === 'CUSTOMER' ? [{ key: '/me/orders', icon: <InboxOutlined />, label: 'Đơn của tôi' }] : []),
    ...(user.role === 'CUSTOMER' || user.role === 'STORE_OWNER'
      ? [{ key: '/me/store', icon: <ShopOutlined />, label: 'Cửa hàng của tôi' }]
      : []),
  ];

  return (
    <div className="account-layout">
      <aside className="account-aside">
        <Typography.Text type="secondary">TÀI KHOẢN CỦA TÔI</Typography.Text>
        <Menu mode="inline" selectedKeys={[selected]} items={visibleItems} onClick={({ key }) => navigate(key)} />
      </aside>
      <section className="account-content"><Outlet /></section>
    </div>
  );
}
