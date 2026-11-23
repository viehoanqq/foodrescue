import { useState } from 'react';
import { Badge, Button, Drawer, Dropdown, Layout, Menu, Typography } from 'antd';
import {
  AppstoreOutlined, AuditOutlined, BankOutlined, DashboardOutlined, DollarOutlined,
  GiftOutlined, InboxOutlined, LogoutOutlined, MenuOutlined, DownOutlined, ProfileOutlined, ReloadOutlined,
  ShopOutlined, ShoppingCartOutlined, TeamOutlined, UserOutlined,
} from '@ant-design/icons';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { homeForRole, loginForRole } from '../routes/paths';
import { useDemo } from '../demo/useDemo';
import './layout.css';

const customerMenu = [
  { key: '/', icon: <ShopOutlined />, label: 'Trang chủ' },
  { key: '/stores', icon: <ShopOutlined />, label: 'Cửa hàng' },
  { key: '/batches', icon: <GiftOutlined />, label: 'Túi giải cứu' },
  { key: '/customer/orders', icon: <InboxOutlined />, label: 'Đơn của tôi' },
];
const staffMenu = [
  { key: '/store/orders', icon: <ProfileOutlined />, label: 'Đơn cửa hàng' },
  { key: '/store/pickup', icon: <ShopOutlined />, label: 'Nhận hàng' },
  { key: '/store/batches', icon: <GiftOutlined />, label: 'Túi giải cứu' },
];
const ownerMenu = [
  ...staffMenu,
  { key: '/store/categories', icon: <AppstoreOutlined />, label: 'Danh mục' },
  { key: '/store/profile', icon: <ShopOutlined />, label: 'Hồ sơ cửa hàng' },
  { key: '/store/staff', icon: <TeamOutlined />, label: 'Nhân viên' },
  { key: '/store/revenue', icon: <DollarOutlined />, label: 'Doanh thu' },
  { key: '/store/reviews', icon: <AuditOutlined />, label: 'Đánh giá' },
];
const adminMenu = [
  { key: '/admin/dashboard', icon: <DashboardOutlined />, label: 'Tổng quan' },
  { key: '/admin/stores', icon: <ShopOutlined />, label: 'Cửa hàng' },
  { key: '/admin/orders', icon: <InboxOutlined />, label: 'Đơn hàng' },
  { key: '/admin/refunds', icon: <ReloadOutlined />, label: 'Hoàn tiền' },
  { key: '/admin/payouts', icon: <BankOutlined />, label: 'Chi trả' },
  { key: '/admin/finance', icon: <DollarOutlined />, label: 'Đối soát' },
  { key: '/admin/users', icon: <UserOutlined />, label: 'Tài khoản' },
  { key: '/admin/audit', icon: <AuditOutlined />, label: 'Nhật ký' },
];

export default function AppLayout() {
  const { user, logout } = useAuth();
  const demo = useDemo();
  const navigate = useNavigate();
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const sideLayout = user?.role === 'ADMIN' || user?.role === 'STORE_OWNER';
  const menu = user?.role === 'ADMIN' ? adminMenu : user?.role === 'STORE_OWNER' ? ownerMenu
    : user?.role === 'STORE_STAFF' ? staffMenu : user?.role === 'CUSTOMER' ? customerMenu : customerMenu.filter((item) => item.key !== '/customer/orders');
  const areaName = user?.role === 'ADMIN' ? 'Quản trị' : user?.role === 'STORE_OWNER' ? 'Chủ cửa hàng'
    : user?.role === 'STORE_STAFF' ? 'Nhân viên cửa hàng' : 'Khách hàng';
  const cartCount = demo.cart.reduce((total, item) => total + (Number(item.quantity) || 0), 0);
  const selected = menu.filter((item) => location.pathname === item.key || (item.key !== '/' && location.pathname.startsWith(`${item.key}/`)))
    .sort((a, b) => b.key.length - a.key.length)[0];

  function selectMenu({ key }) {
    setDrawerOpen(false);
    navigate(key);
  }

  function signOut() {
    const loginPath = loginForRole(user?.role);
    logout();
    navigate(loginPath, { replace: true });
  }

  const accountItems = [
    { key: 'profile', icon: <UserOutlined />, label: 'Hồ sơ cá nhân', onClick: () => navigate('/me') },
    ...(user?.role === 'CUSTOMER' || user?.role === 'STORE_OWNER'
      ? [{ key: 'store', icon: <ShopOutlined />, label: 'Cửa hàng của tôi', onClick: () => navigate('/me/store') }]
      : []),
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: 'Đăng xuất', onClick: signOut },
  ];

  const navigation = <>
    <div className="app-sidebar-heading">Điều hướng</div>
    <Menu mode="inline" selectedKeys={selected ? [selected.key] : []} items={menu} onClick={selectMenu} />
    <div className="app-sidebar-bottom">
      {user ? <><Link to="/me" onClick={() => setDrawerOpen(false)}><UserOutlined /> Tài khoản của tôi</Link>
        <Button block onClick={signOut}>Đăng xuất</Button></>
        : <><Link to="/login" onClick={() => setDrawerOpen(false)}>Đăng nhập</Link><Link to="/register" onClick={() => setDrawerOpen(false)}>Đăng ký</Link></>}
    </div>
  </>;

  return <Layout className={`app-shell ${sideLayout ? 'app-shell-side' : 'app-shell-top'}`}>
    {sideLayout && <Layout.Sider width={244} theme="light" className="app-desktop-sidebar">
      <div className="app-sidebar-inner">
        <Link to={homeForRole(user?.role)} className="app-brand" aria-label="FoodRescue"><img src="/foodrescue-logo.png" alt="FoodRescue" /></Link>
        <Typography.Text type="secondary" className="app-sidebar-subtitle">{areaName}</Typography.Text>
        {navigation}
      </div>
    </Layout.Sider>}
    <Layout className="app-main">
      <Layout.Header className="app-topbar">
        <div className="app-topbar-inner">
          <Button className="app-mobile-menu" type="text" icon={<MenuOutlined />} aria-label="Mở menu" onClick={() => setDrawerOpen(true)} />
          {sideLayout ? <Typography.Text strong className="app-page-name">{selected?.label || (location.pathname.startsWith('/me') ? 'Tài khoản' : areaName)}</Typography.Text>
            : <>
              <Link to={homeForRole(user?.role)} className="app-top-brand" aria-label="FoodRescue"><img src="/foodrescue-logo.png" alt="FoodRescue" /></Link>
              <Menu className="app-top-menu" theme="dark" mode="horizontal" selectedKeys={selected ? [selected.key] : []}
                items={menu} onClick={selectMenu} />
            </>}
          {!sideLayout && user?.role === 'CUSTOMER' && <Badge count={cartCount} size="small" offset={[-2, 2]}>
            <Link className="app-cart-link" to="/customer/cart"><ShoppingCartOutlined /> <span>Giỏ hàng</span></Link>
          </Badge>}
          {!sideLayout && user && <Dropdown menu={{ items: accountItems }} trigger={['click']} placement="bottomRight">
            <Button type="text" className="app-current-user"><UserOutlined /> {user.fullName || user.email || 'Tài khoản'} <DownOutlined /></Button>
          </Dropdown>}
          {!user && <div className="app-auth-links"><Link to="/login">Đăng nhập</Link><Link to="/register">Đăng ký</Link></div>}
          {sideLayout && <Link to="/me" className="app-current-user"><UserOutlined /> {user.fullName || user.email || 'Tài khoản'}</Link>}
        </div>
      </Layout.Header>
      <Layout.Content className="app-content"><Outlet /></Layout.Content>
      {!sideLayout && <Layout.Footer className="app-footer">
        <div className="app-footer-intro"><strong>FoodRescue</strong><span>Giải cứu thực phẩm còn tốt, cùng giảm lãng phí mỗi ngày.</span><span>Kết nối cửa hàng địa phương với những bữa ăn chất lượng.</span></div>
        <nav aria-label="Liên kết cuối trang"><Link to="/stores">Cửa hàng</Link><Link to="/batches">Túi giải cứu</Link><Link to={user ? '/me' : '/login'}>{user ? 'Tài khoản' : 'Đăng nhập'}</Link></nav>
        <span>© 2026 FoodRescue</span>
      </Layout.Footer>}
    </Layout>
    <Drawer title="FoodRescue" placement="left" open={drawerOpen} onClose={() => setDrawerOpen(false)} width={264} className="app-mobile-drawer">
      <div className="app-sidebar-inner">{navigation}</div>
    </Drawer>
  </Layout>;
}
