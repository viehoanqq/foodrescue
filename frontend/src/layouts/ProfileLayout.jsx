import { Menu, Typography } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import './layout.css';

const items = [
  { key: '/me', icon: <UserOutlined />, label: 'Hồ sơ cá nhân' },
  { key: '/me/password', icon: <LockOutlined />, label: 'Đổi mật khẩu' },
];

export default function ProfileLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const selected = location.pathname === '/me/password' ? '/me/password' : '/me';

  return <div className="account-layout">
    <aside className="account-aside">
      <Typography.Text type="secondary">TÀI KHOẢN CỦA TÔI</Typography.Text>
      <Menu mode="inline" selectedKeys={[selected]} items={items} onClick={({ key }) => navigate(key)} />
    </aside>
    <section className="account-content"><Outlet /></section>
  </div>;
}
