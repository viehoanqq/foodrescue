import { Menu, Typography } from 'antd';
import { LockOutlined, ShopOutlined, UserOutlined } from '@ant-design/icons';
import { useAuth } from '../auth/AuthProvider';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import './layout.css';

const items = [
  { key: '/me', icon: <UserOutlined />, label: 'Hồ sơ cá nhân' },
  { key: '/me/password', icon: <LockOutlined />, label: 'Đổi mật khẩu' },
];

export default function ProfileLayout() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const location = useLocation();
  const selected = location.pathname === '/me/password' ? '/me/password' : location.pathname === '/me/store' ? '/me/store' : '/me';
  const visibleItems = user.role === 'CUSTOMER' || user.role === 'STORE_OWNER'
    ? [...items, { key: '/me/store', icon: <ShopOutlined />, label: 'Cửa hàng của tôi' }] : items;

  return <div className="account-layout">
    <aside className="account-aside">
      <Typography.Text type="secondary">TÀI KHOẢN CỦA TÔI</Typography.Text>
      <Menu mode="inline" selectedKeys={[selected]} items={visibleItems} onClick={({ key }) => navigate(key)} />
    </aside>
    <section className="account-content"><Outlet /></section>
  </div>;
}
