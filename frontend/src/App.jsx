import { useState, useEffect } from 'react';
import { Layout, Menu, Typography, Select, Space, Tag } from 'antd';
import {
  ShoppingCartOutlined,
  InboxOutlined,
  ShopOutlined,
  ProfileOutlined,
  UserOutlined,
} from '@ant-design/icons';
import CartView from './features/order/CartView';
import MyOrdersView from './features/order/MyOrdersView';
import StorePickupView from './features/order/StorePickupView';
import StoreOrdersView from './features/order/StoreOrdersView';
import PaymentResultView from './features/order/PaymentResultView';
import HomeCatalogView from './features/catalog/HomeCatalogView';
import RescueBatchDetailView from './features/catalog/RescueBatchDetailView';
import OrderInvoiceDetailView from './features/order/OrderInvoiceDetailView';

const { Header, Content, Footer } = Layout;
const { Title, Text } = Typography;

const USERS = [
  { id: '8', name: 'Ma Lý Hoàng Ân (Khách hàng)', role: 'CUSTOMER', store: null },
  { id: '9', name: 'Trần Hoàng Linh (Khách hàng)', role: 'CUSTOMER', store: null },
  { id: '6', name: 'Đỗ Minh Khoa (Nhân viên An Phát)', role: 'STORE_STAFF', store: 'An Phát' },
  { id: '2', name: 'Nguyễn Văn Phát (Chủ An Phát)', role: 'STORE_OWNER', store: 'An Phát' },
  { id: '3', name: 'Lê Thị Thu Thảo (Chủ Sweet Paris)', role: 'STORE_OWNER', store: 'Sweet Paris' },
  { id: '1', name: 'Quản Trị Viên (Admin)', role: 'ADMIN', store: null },
];

export default function App() {
  const [currentUserId, setCurrentUserId] = useState(() => localStorage.getItem('fr_dev_user_id') || '8');
  const currentUserObj = USERS.find((u) => u.id === currentUserId) || USERS[0];
  const isStoreSide = currentUserObj.role === 'STORE_STAFF' || currentUserObj.role === 'STORE_OWNER';

  const [activeTab, setActiveTab] = useState(() => (isStoreSide ? 'store-orders' : 'home'));
  const [selectedBatchId, setSelectedBatchId] = useState(null);
  const [selectedOrderId, setSelectedOrderId] = useState(null);
  const isPaymentResult = window.location.pathname.includes('/payment/result') || window.location.search.includes('vnp_');

  useEffect(() => {
    localStorage.setItem('fr_dev_user_id', currentUserId);
  }, [currentUserId]);

  // Đảm bảo tab được chọn luôn hợp lệ theo vai trò của người dùng
  useEffect(() => {
    if (isStoreSide) {
      if (activeTab === 'home' || activeTab === 'cart' || activeTab === 'my-orders') {
        setActiveTab('store-orders');
      }
    } else {
      if (activeTab === 'store-orders' || activeTab === 'counter') {
        setActiveTab('home');
      }
    }
  }, [isStoreSide, activeTab]);

  const handleUserChange = (val) => {
    setCurrentUserId(val);
    localStorage.setItem('fr_dev_user_id', val);
    const targetUser = USERS.find((u) => u.id === val);
    if (targetUser && (targetUser.role === 'STORE_STAFF' || targetUser.role === 'STORE_OWNER')) {
      setActiveTab('store-orders');
    } else {
      setActiveTab('home');
    }
    window.location.reload();
  };

  // PHÂN QUYỀN MENU: Khách hàng thấy Trang chủ & Đặt túi, Đơn của tôi. Cửa hàng chỉ thấy Quầy & Đơn của quán.
  const menuItems = isStoreSide
    ? [
        { key: 'store-orders', icon: <ProfileOutlined />, label: 'Đơn của cửa hàng' },
        { key: 'counter', icon: <ShopOutlined />, label: 'Màn hình quầy (Giao hàng)' },
      ]
    : [
        { key: 'home', icon: <ShopOutlined />, label: 'Trang chủ & Đặt túi cứu thực phẩm' },
        { key: 'cart', icon: <ShoppingCartOutlined />, label: 'Giỏ hàng chi tiết' },
        { key: 'my-orders', icon: <InboxOutlined />, label: 'Đơn của tôi & Mã nhận hàng' },
      ];

  return (
    <Layout style={{ minHeight: '100vh', background: '#f5f5f5' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: '#fff',
          padding: '0 24px',
          boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
          position: 'sticky',
          top: 0,
          zIndex: 100,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <Title
            level={3}
            style={{ margin: 0, color: '#fa8c16', cursor: 'pointer' }}
            onClick={() => {
              if (!isStoreSide) {
                setSelectedBatchId(null);
                setActiveTab('home');
              }
            }}
          >
            🥑 FoodRescue
          </Title>
          <Text type="secondary" style={{ fontSize: 13 }}>
            {isStoreSide ? `Kênh cửa hàng (${currentUserObj.store})` : 'Kênh khách hàng'}
          </Text>
        </div>

        <Menu
          mode="horizontal"
          selectedKeys={[isPaymentResult ? '' : activeTab]}
          onClick={(e) => {
            setSelectedBatchId(null);
            setActiveTab(e.key);
          }}
          items={menuItems}
          style={{ flex: 1, minWidth: 0, justifyContent: 'center', borderBottom: 'none' }}
        />

        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {isStoreSide ? (
            <Tag color="orange" style={{ margin: 0 }}>Cửa hàng: {currentUserObj.store}</Tag>
          ) : (
            <Tag color="green" style={{ margin: 0 }}>Khách hàng</Tag>
          )}
          <UserOutlined style={{ color: '#8c8c8c' }} />
          <Select
            value={currentUserId}
            onChange={handleUserChange}
            style={{ width: 260 }}
            options={USERS.map((u) => ({
              value: u.id,
              label: (
                <Space>
                  <span>{u.name}</span>
                </Space>
              ),
            }))}
          />
        </div>
      </Header>

      <Content style={{ padding: (activeTab === 'home' || selectedBatchId || selectedOrderId) ? '0' : '24px', maxWidth: (activeTab === 'home' || selectedBatchId || selectedOrderId) ? '100%' : 1200, margin: '0 auto', width: '100%' }}>
        {isPaymentResult ? (
          <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
            <PaymentResultView onGoToOrders={() => {
              window.history.pushState({}, '', '/');
              setSelectedBatchId(null);
              setSelectedOrderId(null);
              setActiveTab('my-orders');
              window.location.reload();
            }} />
          </div>
        ) : (
          <>
            {selectedOrderId ? (
              <OrderInvoiceDetailView
                orderId={selectedOrderId}
                onBack={() => setSelectedOrderId(null)}
              />
            ) : selectedBatchId ? (
              <RescueBatchDetailView
                batchId={selectedBatchId}
                onBack={() => setSelectedBatchId(null)}
                onGoToOrders={() => {
                  setSelectedBatchId(null);
                  setActiveTab('my-orders');
                }}
              />
            ) : (
              <>
                {activeTab === 'home' && (
                  <HomeCatalogView
                    onGoToOrders={() => setActiveTab('my-orders')}
                    onSelectBatch={(id) => setSelectedBatchId(id)}
                    onSelectOrder={(id) => setSelectedOrderId(id)}
                  />
                )}
                {activeTab === 'cart' && (
                  <CartView onOrderCreated={() => setActiveTab('my-orders')} />
                )}
                {activeTab === 'my-orders' && (
                  <MyOrdersView onSelectOrder={(id) => setSelectedOrderId(id)} />
                )}
                {activeTab === 'counter' && (
                  <StorePickupView currentUser={currentUserObj} onSwitchUser={handleUserChange} />
                )}
                {activeTab === 'store-orders' && (
                  <StoreOrdersView currentUser={currentUserObj} onSwitchUser={handleUserChange} />
                )}
              </>
            )}
          </>
        )}
      </Content>

      <Footer style={{ textAlign: 'center', color: '#8c8c8c' }}>
        FoodRescue &copy; 2026 - Đồ án J2EE · Phân quyền: Khách hàng chỉ truy cập đơn của mình · Cửa hàng chỉ truy cập đơn của quán mình
      </Footer>
    </Layout>
  );
}
