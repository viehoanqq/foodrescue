import { Button, Layout, Menu, Space, Typography } from 'antd';
import { InboxOutlined, ProfileOutlined, ShopOutlined, UserOutlined, ShoppingCartOutlined, GiftOutlined, TeamOutlined, DollarOutlined, DashboardOutlined, AppstoreOutlined, AuditOutlined, BankOutlined, ReloadOutlined } from '@ant-design/icons';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { homeForRole, STORE_ROLES } from '../routes/paths';

const { Header, Content, Footer } = Layout;
const { Title, Text } = Typography;

const customerMenu = [
  { key: '/customer', icon: <ShopOutlined />, label: 'Trang chủ' },
  { key: '/customer/batches', icon: <GiftOutlined />, label: 'Túi giải cứu' },
  { key: '/customer/cart', icon: <ShoppingCartOutlined />, label: 'Giỏ hàng' },
  { key: '/customer/orders', icon: <InboxOutlined />, label: 'Đơn của tôi' },
];
const storeMenu = [
  { key: '/store/orders', icon: <ProfileOutlined />, label: 'Đơn cửa hàng' },
  { key: '/store/pickup', icon: <ShopOutlined />, label: 'Quầy giao hàng' },
  { key: '/store/batches', icon: <GiftOutlined />, label: 'Túi giải cứu' },
  { key: '/store/profile', icon: <ShopOutlined />, label: 'Hồ sơ' },
  { key: '/store/staff', icon: <TeamOutlined />, label: 'Nhân viên' },
  { key: '/store/revenue', icon: <DollarOutlined />, label: 'Doanh thu' },
  { key: '/store/reviews', icon: <AuditOutlined />, label: 'Đánh giá' },
];
const adminMenu = [
  { key: '/admin/dashboard', icon: <DashboardOutlined />, label: 'Tổng quan' },
  { key: '/admin/stores', icon: <ShopOutlined />, label: 'Cửa hàng' },
  { key: '/admin/categories', icon: <AppstoreOutlined />, label: 'Danh mục' },
  { key: '/admin/orders', icon: <InboxOutlined />, label: 'Đơn hàng' },
  { key: '/admin/refunds', icon: <ReloadOutlined />, label: 'Hoàn tiền' },
  { key: '/admin/payouts', icon: <BankOutlined />, label: 'Chi trả' },
  { key: '/admin/finance', icon: <DollarOutlined />, label: 'Đối soát' },
  { key: '/admin/users', icon: <UserOutlined />, label: 'Tài khoản' },
  { key: '/admin/audit', icon: <AuditOutlined />, label: 'Nhật ký' },
];

export default function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const isStore = STORE_ROLES.includes(user.role);
  const menu = user.role === 'ADMIN' ? adminMenu : isStore ? storeMenu : customerMenu;
  const selected = menu.findLast((item) => location.pathname === item.key || location.pathname.startsWith(`${item.key}/`));

  function signOut() {
    logout();
    navigate('/login', { replace: true });
  }

  return <Layout style={{ minHeight: '100vh', background: '#f5f5f5' }}>
    <Header style={{ background: '#fff', padding: '0 24px', position: 'sticky', top: 0, zIndex: 100, height: 'auto', minHeight: 72 }}>
      <div style={{ maxWidth: 1180, margin: '0 auto', display: 'flex', alignItems: 'center', justifyContent: 'space-between', minHeight: 72, flexWrap: 'wrap', gap: 12 }}>
      <Space>
        <Link to={homeForRole(user.role)}><Title level={3} style={{ margin: 0, color: '#fa8c16' }}>FoodRescue</Title></Link>
        <Text type="secondary">{user.role === 'ADMIN' ? 'Quản trị' : isStore ? 'Kênh cửa hàng' : 'Kênh khách hàng'}</Text>
      </Space>
      <Menu mode="horizontal" selectedKeys={selected ? [selected.key] : []} onClick={({ key }) => navigate(key)}
        items={menu} style={{ flex: 1, minWidth: 0, justifyContent: 'center', borderBottom: 'none' }} />
      <Space><UserOutlined /><Link to="/me"><Text>{user.fullName}</Text></Link><Button onClick={signOut}>Đăng xuất</Button></Space>
      </div>
    </Header>
    <Content style={{ padding: '0 24px', maxWidth: 1200,
      margin: '0 auto', width: '100%' }}><Outlet /></Content>
    <Footer style={{ textAlign: 'center', color: '#8c8c8c' }}>FoodRescue © 2026</Footer>
  </Layout>;
}
