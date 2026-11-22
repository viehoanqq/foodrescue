import { useState } from 'react';
import { Button, Drawer, Layout, Menu, Typography } from 'antd';
import {
  AppstoreOutlined, AuditOutlined, BankOutlined, DashboardOutlined, DollarOutlined,
  GiftOutlined, InboxOutlined, MenuOutlined, ProfileOutlined, ReloadOutlined,
  ShopOutlined, ShoppingCartOutlined, TeamOutlined, UserOutlined,
} from '@ant-design/icons';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { homeForRole, loginForRole } from '../routes/paths';
import './layout.css';

const customerMenu = [
  { key: '/customer', icon: <ShopOutlined />, label: 'Trang chủ' },
  { key: '/customer/batches', icon: <GiftOutlined />, label: 'Túi giải cứu' },
  { key: '/customer/cart', icon: <ShoppingCartOutlined />, label: 'Giỏ hàng' },
  { key: '/customer/orders', icon: <InboxOutlined />, label: 'Đơn của tôi' },
];
const staffMenu = [
  { key: '/store/orders', icon: <ProfileOutlined />, label: 'Đơn cửa hàng' },
  { key: '/store/pickup', icon: <ShopOutlined />, label: 'Nhận hàng' },
  { key: '/store/batches', icon: <GiftOutlined />, label: 'Túi giải cứu' },
];
const ownerMenu = [
  ...staffMenu,
  { key: '/store/profile', icon: <ShopOutlined />, label: 'Hồ sơ cửa hàng' },
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
  const [drawerOpen, setDrawerOpen] = useState(false);
  const sideLayout = user.role === 'ADMIN' || user.role === 'STORE_OWNER';
  const menu = user.role === 'ADMIN' ? adminMenu : user.role === 'STORE_OWNER' ? ownerMenu
    : user.role === 'STORE_STAFF' ? staffMenu : customerMenu;
  const areaName = user.role === 'ADMIN' ? 'Quản trị' : user.role === 'STORE_OWNER' ? 'Chủ cửa hàng'
    : user.role === 'STORE_STAFF' ? 'Nhân viên cửa hàng' : 'Khách hàng';
  const selected = menu.filter((item) => location.pathname === item.key || location.pathname.startsWith(`${item.key}/`))
    .sort((a, b) => b.key.length - a.key.length)[0];

  function selectMenu({ key }) {
    setDrawerOpen(false);
    navigate(key);
  }

  function signOut() {
    const loginPath = loginForRole(user.role);
    logout();
    navigate(loginPath, { replace: true });
  }

  const navigation = <>
    <div className="app-sidebar-heading">Điều hướng</div>
    <Menu mode="inline" selectedKeys={selected ? [selected.key] : []} items={menu} onClick={selectMenu} />
    <div className="app-sidebar-bottom">
      <Link to="/me" onClick={() => setDrawerOpen(false)}><UserOutlined /> Tài khoản của tôi</Link>
      <Button block onClick={signOut}>Đăng xuất</Button>
    </div>
  </>;

  return <Layout className={`app-shell ${sideLayout ? 'app-shell-side' : 'app-shell-top'}`}>
    {sideLayout && <Layout.Sider width={244} theme="light" className="app-desktop-sidebar">
      <div className="app-sidebar-inner">
        <Link to={homeForRole(user.role)} className="app-brand" aria-label="FoodRescue"><img src="/foodrescue-logo.png" alt="FoodRescue" /></Link>
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
              <Link to={homeForRole(user.role)} className="app-top-brand" aria-label="FoodRescue"><img src="/foodrescue-logo.png" alt="FoodRescue" /></Link>
              <Menu className="app-top-menu" theme="dark" mode="horizontal" selectedKeys={selected ? [selected.key] : []}
                items={menu} onClick={selectMenu} />
            </>}
          <Link to="/me" className="app-current-user"><UserOutlined /> {user.fullName}</Link>
          {!sideLayout && <Button className="app-top-logout" onClick={signOut}>Đăng xuất</Button>}
        </div>
      </Layout.Header>
      <Layout.Content className="app-content"><Outlet /></Layout.Content>
      {user.role !== 'ADMIN' && <Layout.Footer className="app-footer">
        <div><strong>FoodRescue</strong><span>Giải cứu thực phẩm còn tốt, cùng giảm lãng phí mỗi ngày.</span></div>
        <span>© 2026 FoodRescue</span>
      </Layout.Footer>}
    </Layout>
    <Drawer title="FoodRescue" placement="left" open={drawerOpen} onClose={() => setDrawerOpen(false)} width={264} className="app-mobile-drawer">
      <div className="app-sidebar-inner">{navigation}</div>
    </Drawer>
  </Layout>;
}
