import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Button,
  Tag,
  Typography,
  Space,
  Select,
  Modal,
  Alert,
  message,
  Divider,
} from 'antd';
import {
  ShoppingCartOutlined,
  SearchOutlined,
  StarFilled,
  QrcodeOutlined,
  CreditCardOutlined,
  CompassOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { QRCodeSVG } from 'qrcode.react';
import { cartApi, orderApi, paymentApi } from '../order/api';
import { formatMoney, formatPickupWindow } from '../../shared/utils/format';

const { Title, Text, Paragraph } = Typography;

// Dữ liệu mẫu khớp đúng database foodrescue_schema.sql và hình mẫu
const INITIAL_BATCHES = [
  {
    id: 1,
    title: 'Túi Bánh Mì Bất Ngờ',
    storeName: 'Bánh Mì Tươi An Phát',
    storeAddress: '105 An Dương Vương, Quận 5',
    category: 'banh-mi',
    originalPrice: 80000,
    rescuePrice: 30000,
    discount: '-62.5%',
    remainingQuantity: 8,
    pickupStart: '2026-09-25T20:30:00',
    pickupEnd: '2026-09-25T21:30:00',
    distance: '850m',
    rating: 4.8,
    reviewsCount: 12,
    description: 'Thành phần ngẫu nhiên: 2 bánh mì hoa cúc mini + 1 sandwich xúc xích trong ngày. Chuẩn VSATTP.',
    status: 'AVAILABLE',
    imageUrl: 'https://images.unsplash.com/photo-1509440159596-0249088772ff?w=600&auto=format&fit=crop&q=80',
  },
  {
    id: 3,
    title: 'Surprise Box Bánh Pháp',
    storeName: 'Sweet Paris Bakery & Coffee',
    storeAddress: '45 Lê Duẩn, Bến Nghé, Quận 1',
    category: 'banh-ngot',
    originalPrice: 120000,
    rescuePrice: 45000,
    discount: '-62.5%',
    remainingQuantity: 14,
    pickupStart: '2026-09-25T21:00:00',
    pickupEnd: '2026-09-25T22:00:00',
    distance: '2.4 km',
    rating: 4.9,
    reviewsCount: 28,
    description: 'Hộp bánh thơm ngon: ngẫu nhiên 3 bánh croissant phô mai / tart trứng / donut kem cao cấp.',
    status: 'AVAILABLE',
    imageUrl: 'https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=600&auto=format&fit=crop&q=80',
  },
  {
    id: 4,
    title: 'Bánh Kem Lát Mini Cao Cấp',
    storeName: 'Sweet Paris Bakery & Coffee',
    storeAddress: '45 Lê Duẩn, Quận 1',
    category: 'banh-ngot',
    originalPrice: 110000,
    rescuePrice: 42000,
    discount: '-61.8%',
    remainingQuantity: 6,
    pickupStart: '2026-09-25T21:00:00',
    pickupEnd: '2026-09-25T22:00:00',
    distance: '2.4 km',
    rating: 4.9,
    reviewsCount: 28,
    description: 'Món tráng miệng hảo hạng: bánh kem bắp và tiramisu lát, bảo quản tủ mát tiêu chuẩn.',
    status: 'AVAILABLE',
    imageUrl: 'https://images.unsplash.com/photo-1578985545062-69928b1d9587?w=600&auto=format&fit=crop&q=80',
  },
  {
    id: 5,
    title: 'Suất Cơm Sườn Bì Chả Đặc Biệt',
    storeName: 'Cơm Tấm Truyền Thống 365',
    storeAddress: '215 Điện Biên Phủ, Bình Thạnh',
    category: 'com',
    originalPrice: 65000,
    rescuePrice: 25000,
    discount: '-61.5%',
    remainingQuantity: 18,
    pickupStart: '2026-09-25T20:00:00',
    pickupEnd: '2026-09-25T21:00:00',
    distance: '3.1 km',
    rating: 4.7,
    reviewsCount: 40,
    description: 'Suất cơm sườn nóng hổi gồm sườn nướng mật ong, bì, chả trứng, canh chua nóng.',
    status: 'AVAILABLE',
    imageUrl: 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=600&auto=format&fit=crop&q=80',
  },
  {
    id: 2,
    title: 'Combo Hamburger Bò & Phô Mai Cuối Giờ',
    storeName: 'Bánh Mì Tươi An Phát',
    storeAddress: '105 An Dương Vương, Quận 5',
    category: 'banh-mi',
    originalPrice: 95000,
    rescuePrice: 40000,
    discount: '-57.9%',
    remainingQuantity: 0,
    pickupStart: '2026-09-23T20:30:00',
    pickupEnd: '2026-09-23T21:30:00',
    distance: '850m',
    rating: 4.8,
    reviewsCount: 12,
    description: '2 burger bò nướng phô mai cheddar giữ nóng tại quầy.',
    status: 'SOLD_OUT',
    imageUrl: 'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=600&auto=format&fit=crop&q=80',
  },
  {
    id: 6,
    title: 'Hộp Rau Củ Quả Hữu Cơ Cuối Ngày',
    storeName: 'GreenMart Organic Farm',
    storeAddress: '12 Nguyễn Thị Minh Khai, Quận 1',
    category: 'rau-cu',
    originalPrice: 60000,
    rescuePrice: 29000,
    discount: '-51.7%',
    remainingQuantity: 0,
    pickupStart: '2026-09-26T18:00:00',
    pickupEnd: '2026-09-26T20:00:00',
    distance: '1.2 km',
    rating: 5.0,
    reviewsCount: 2,
    description: 'Rau cải ngọt, cà chua bi và cà rốt sạch chuẩn VietGAP.',
    status: 'PENDING_APPROVAL',
    imageUrl: 'https://images.unsplash.com/photo-1610348725531-843dff563e2c?w=600&auto=format&fit=crop&q=80',
  },
];

const CATEGORIES = [
  { key: 'all', label: '🥗 Tất cả (6)' },
  { key: 'banh-mi', label: '🥖 Bánh mì & Sandwich (2)' },
  { key: 'banh-ngot', label: '🥐 Bánh ngọt & Pastry (2)' },
  { key: 'com', label: '🍱 Cơm & Món chính (1)' },
  { key: 'rau-cu', label: '🥦 Rau củ & Trái cây (1)' },
  { key: 'do-uong', label: '🥤 Đồ uống & Tráng miệng (0)' },
  { key: 'tap-hoa', label: '🛒 Tạp hoá (0)' },
];

export default function HomeCatalogView({ onGoToOrders, onSelectBatch, onSelectOrder }) {
  const [selectedCategory, setSelectedCategory] = useState('all');
  const [districtFilter, setDistrictFilter] = useState('all');
  const [timeFilter, setTimeFilter] = useState('today');
  const [cart, setCart] = useState(null);
  const [addingId, setAddingId] = useState(null);
  const [checkingOut, setCheckingOut] = useState(false);
  const [activeOrder, setActiveOrder] = useState(null);
  const [qrModalVisible, setQrModalVisible] = useState(false);

  // Tải giỏ hàng
  const loadCart = useCallback(async () => {
    try {
      const data = await cartApi.getCart();
      setCart(data);
    } catch {
      // Bỏ qua lỗi kết nối khi chưa đăng nhập
    }
  }, []);

  // Tải đơn hàng chờ nhận gần nhất
  const loadActiveOrder = useCallback(async () => {
    try {
      const orders = await orderApi.getMyOrders('CONFIRMED');
      if (orders && orders.length > 0) {
        setActiveOrder(orders[0]);
      } else {
        // Fallback đơn mẫu từ schema nếu có
        setActiveOrder({
          orderCode: 'FR260925-0004',
          pickupCode: 'H2R9W7',
          storeName: 'Sweet Paris Bakery & Coffee',
          storeAddress: '45 Lê Duẩn, Quận 1',
          pickupStart: '2026-09-25T21:00:00',
          pickupEnd: '2026-09-25T22:00:00',
          totalAmount: 87000,
        });
      }
    } catch {
      setActiveOrder({
        orderCode: 'FR260925-0004',
        pickupCode: 'H2R9W7',
        storeName: 'Sweet Paris Bakery & Coffee',
        storeAddress: '45 Lê Duẩn, Quận 1',
        pickupStart: '2026-09-25T21:00:00',
        pickupEnd: '2026-09-25T22:00:00',
        totalAmount: 87000,
      });
    }
  }, []);

  useEffect(() => {
    loadCart();
    loadActiveOrder();
  }, [loadCart, loadActiveOrder]);

  // Thêm vào giỏ hàng
  const handleAddToCart = async (batch) => {
    setAddingId(batch.id);
    try {
      const updated = await cartApi.addToCart(batch.id, 1);
      setCart(updated);
      message.success(`Đã thêm "${batch.title}" vào giỏ hàng!`);
    } catch (err) {
      message.error(err.message || 'Không thể thêm vào giỏ hàng');
    } finally {
      setAddingId(null);
    }
  };

  // Đặt ngay & chuyển thanh toán VNPay Sandbox
  const handleCheckout = async () => {
    if (!cart || !cart.items || cart.items.length === 0) {
      message.warning('Giỏ hàng đang trống!');
      return;
    }
    setCheckingOut(true);
    try {
      const newOrder = await orderApi.createOrder();
      message.success(`Tạo đơn ${newOrder.orderCode} thành công! Khởi tạo cổng VNPay Sandbox...`);
      loadCart();
      const payRes = await paymentApi.createPaymentUrl(newOrder.id);
      if (payRes.paymentUrl) {
        window.open(payRes.paymentUrl, '_blank');
      }
    } catch (err) {
      message.error(err.message || 'Lỗi khi đặt đơn');
    } finally {
      setCheckingOut(false);
    }
  };

  // Xoá giỏ hàng
  const handleClearCart = async () => {
    try {
      await cartApi.clearCart();
      setCart(null);
      message.success('Đã làm trống giỏ hàng');
      loadCart();
    } catch (err) {
      message.error(err.message);
    }
  };

  // Lọc danh sách túi cứu trợ
  const filteredBatches = INITIAL_BATCHES.filter((b) => {
    if (selectedCategory !== 'all' && b.category !== selectedCategory) return false;
    if (districtFilter === 'q1' && !b.storeAddress.includes('Quận 1')) return false;
    if (districtFilter === 'q5' && !b.storeAddress.includes('Quận 5')) return false;
    if (districtFilter === 'bt' && !b.storeAddress.includes('Bình Thạnh')) return false;
    return true;
  });

  return (
    <div style={{ maxWidth: 1280, margin: '0 auto', fontFamily: 'system-ui, -apple-system, sans-serif' }}>
      {/* 1. HERO SECTION & STATS CARDS */}
      <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 0.8fr', gap: 24, marginBottom: 32 }}>
        {/* Left Hero */}
        <div style={{ background: '#fff', padding: 32, borderRadius: 16, boxShadow: '0 4px 20px rgba(0,0,0,0.04)', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, background: '#e6f7ff', color: '#096dd9', padding: '6px 14px', borderRadius: 20, fontSize: 13, fontWeight: 600, marginBottom: 16 }}>
              <span>🌱 GIẢI CỨU MÓN ĂN CHẤT LƯỢNG CAO VỚI GIÁ GIẢM 50% - 70%</span>
            </div>
            <Title level={1} style={{ margin: '0 0 16px 0', fontSize: 36, lineHeight: 1.25, color: '#141414', fontWeight: 800 }}>
              Cùng Sài Gòn Giải Cứu <span style={{ color: '#52c41a', textDecoration: 'underline wavy #52c41a' }}>Món Ngon</span> Cuối Ngày
            </Title>
            <Paragraph style={{ color: '#595959', fontSize: 15, lineHeight: 1.6, marginBottom: 24 }}>
              Tận hưởng món thơm ngon chất lượng cao từ các tiệm bánh nổi danh, quán ăn trứ danh tại Quận 1, Quận 5 và Bình Thạnh. Tiết kiệm <strong style={{ color: '#fa8c16' }}>50% - 70%</strong> đồng thời góp phần giảm thiểu rác thải cho thành phố!
            </Paragraph>
          </div>

          {/* Quick Filter Bar */}
          <div style={{ background: '#fafafa', padding: 12, borderRadius: 12, border: '1px solid #f0f0f0', display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
            <Select
              value={districtFilter}
              onChange={setDistrictFilter}
              style={{ width: 160 }}
              options={[
                { value: 'all', label: '📍 Khu vực (Tất cả)' },
                { value: 'q1', label: 'Quận 1' },
                { value: 'q5', label: 'Quận 5' },
                { value: 'bt', label: 'Bình Thạnh' },
              ]}
            />
            <Select
              value={timeFilter}
              onChange={setTimeFilter}
              style={{ width: 170 }}
              options={[
                { value: 'today', label: '⏰ Khung giờ (Hôm nay)' },
                { value: '20_21', label: '20:00 - 21:00' },
                { value: '21_22', label: '21:00 - 22:00' },
              ]}
            />
            <Button
              type="primary"
              icon={<SearchOutlined />}
              style={{ background: '#135200', borderColor: '#135200', borderRadius: 8, fontWeight: 600, padding: '0 20px' }}
            >
              Lọc túi
            </Button>
          </div>
        </div>

        {/* Right Stats Grid (4 Cards) */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <div style={{ background: '#fff', padding: 20, borderRadius: 16, boxShadow: '0 2px 12px rgba(0,0,0,0.04)', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: 24 }}>🍽️</span>
              <Tag color="success">Đã cứu</Tag>
            </div>
            <div>
              <div style={{ fontSize: 28, fontWeight: 800, color: '#135200', marginTop: 12 }}>1,420+</div>
              <Text type="secondary" style={{ fontSize: 13 }}>Bữa ăn ngon đã cứu thành công</Text>
            </div>
          </div>

          <div style={{ background: '#fff', padding: 20, borderRadius: 16, boxShadow: '0 2px 12px rgba(0,0,0,0.04)', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: 24 }}>🏷️</span>
              <Tag color="volcano">Tiết kiệm TB</Tag>
            </div>
            <div>
              <div style={{ fontSize: 28, fontWeight: 800, color: '#d4380d', marginTop: 12 }}>-62.5%</div>
              <Text type="secondary" style={{ fontSize: 13 }}>Mức giảm bình quân (50% - 70%)</Text>
            </div>
          </div>

          <div style={{ background: '#fff', padding: 20, borderRadius: 16, boxShadow: '0 2px 12px rgba(0,0,0,0.04)', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: 24 }}>🏪</span>
              <Tag color="blue">Đang mở bán</Tag>
            </div>
            <div>
              <div style={{ fontSize: 28, fontWeight: 800, color: '#096dd9', marginTop: 12 }}>4 Đối tác</div>
              <Text type="secondary" style={{ fontSize: 13 }}>Tại Q.1, Q.5 & Bình Thạnh (An Phát, Sweet Paris...)</Text>
            </div>
          </div>

          <div style={{ background: '#135200', color: '#fff', padding: 20, borderRadius: 16, boxShadow: '0 2px 12px rgba(0,0,0,0.08)', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: 24 }}>⏰</span>
              <Tag color="gold" style={{ background: 'rgba(255,255,255,0.2)', border: 'none', color: '#fff' }}>Khung giờ vàng</Tag>
            </div>
            <div>
              <div style={{ fontSize: 22, fontWeight: 800, color: '#d9f7be', marginTop: 12 }}>20:00 - 22:00</div>
              <Text style={{ fontSize: 13, color: '#d9f7be' }}>Thời điểm nhận hàng rộn ràng nhất tối nay</Text>
            </div>
          </div>
        </div>
      </div>

      {/* 2. CATEGORY PILL SLIDER */}
      <div style={{ marginBottom: 28 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
          <Space>
            <Title level={4} style={{ margin: 0 }}>Danh Mục Túi Cứu</Title>
            <Tag color="default">Chọn nhanh theo loại</Tag>
          </Space>
          <Button type="link" style={{ color: '#52c41a', padding: 0 }}>Xem tất cả 6 đối tác</Button>
        </div>
        <div style={{ display: 'flex', gap: 10, overflowX: 'auto', paddingBottom: 6 }}>
          {CATEGORIES.map((cat) => (
            <Button
              key={cat.key}
              type={selectedCategory === cat.key ? 'primary' : 'default'}
              shape="round"
              onClick={() => setSelectedCategory(cat.key)}
              style={
                selectedCategory === cat.key
                  ? { background: '#135200', borderColor: '#135200', fontWeight: 600 }
                  : { background: '#fff', borderColor: '#d9d9d9' }
              }
            >
              {cat.label}
            </Button>
          ))}
        </div>
      </div>

      {/* 3. MAIN SECTION: 2 COLUMNS (Túi giải cứu & Sidebar Widgets) */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 24, marginBottom: 40 }}>
        {/* Left Column: Grid of Rescue Batches */}
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <div>
              <div style={{ fontSize: 12, fontWeight: 700, color: '#fa8c16', letterSpacing: 1 }}>
                ⚡ ĐẶT TRƯỚC HÔM NAY · VẪN CÒN SUẤT
              </div>
              <Title level={3} style={{ margin: 0, fontWeight: 800 }}>
                Túi Giải Cứu Nổi Bật Đang Mở Bán
              </Title>
            </div>
            <Select
              defaultValue="discount"
              style={{ width: 220 }}
              options={[
                { value: 'discount', label: 'Sắp xếp: Giảm giá cao nhất (70%)' },
                { value: 'distance', label: 'Gần tôi nhất' },
                { value: 'price', label: 'Giá từ thấp đến cao' },
              ]}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 18 }}>
            {filteredBatches.map((item) => {
              const isAvailable = item.status === 'AVAILABLE';
              const isSoldOut = item.status === 'SOLD_OUT';
              const isPending = item.status === 'PENDING_APPROVAL';

              return (
                <Card
                  key={item.id}
                  hoverable
                  style={{
                    borderRadius: 14,
                    overflow: 'hidden',
                    opacity: isSoldOut ? 0.75 : 1,
                    border: '1px solid #f0f0f0',
                  }}
                  bodyStyle={{ padding: 16 }}
                  cover={
                    <div
                      style={{ position: 'relative', height: 170, overflow: 'hidden', cursor: 'pointer' }}
                      onClick={() => onSelectBatch && onSelectBatch(item.id)}
                    >
                      <img
                        alt={item.title}
                        src={item.imageUrl}
                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                      />
                      {/* Discount & Remaining Badge */}
                      <div style={{ position: 'absolute', top: 10, left: 10, display: 'flex', gap: 6 }}>
                        <Tag color="volcano" style={{ fontWeight: 800, margin: 0, fontSize: 12 }}>
                          {item.discount}
                        </Tag>
                        {isAvailable && (
                          <Tag color="blue" style={{ margin: 0, fontSize: 12 }}>
                            Còn {item.remainingQuantity}
                          </Tag>
                        )}
                        {isPending && (
                          <Tag color="cyan" style={{ margin: 0, fontSize: 12 }}>Sắp có</Tag>
                        )}
                      </div>

                      {/* Sold out overlay */}
                      {isSoldOut && (
                        <div
                          style={{
                            position: 'absolute',
                            inset: 0,
                            background: 'rgba(0,0,0,0.5)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                          }}
                        >
                          <Tag color="error" style={{ fontSize: 14, padding: '6px 14px', fontWeight: 800 }}>
                            🚫 ĐÃ HẾT TÚI HÔM NAY
                          </Tag>
                        </div>
                      )}

                      {/* Pending approval overlay */}
                      {isPending && (
                        <div
                          style={{
                            position: 'absolute',
                            inset: 0,
                            background: 'rgba(0,0,0,0.3)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                          }}
                        >
                          <Tag color="default" style={{ fontSize: 12, padding: '4px 10px', background: 'rgba(255,255,255,0.9)', color: '#333' }}>
                            Đang kiểm định an toàn thực phẩm
                          </Tag>
                        </div>
                      )}

                      {/* Bottom Time & Distance overlay */}
                      {isAvailable && (
                        <div
                          style={{
                            position: 'absolute',
                            bottom: 8,
                            left: 8,
                            right: 8,
                            display: 'flex',
                            justifyContent: 'space-between',
                          }}
                        >
                          <span
                            style={{
                              background: 'rgba(0,0,0,0.7)',
                              color: '#fff',
                              fontSize: 11,
                              padding: '2px 8px',
                              borderRadius: 4,
                            }}
                          >
                            ⏰ {formatPickupWindow(item.pickupStart, item.pickupEnd)}
                          </span>
                          <span
                            style={{
                              background: 'rgba(0,100,0,0.85)',
                              color: '#d9f7be',
                              fontSize: 11,
                              padding: '2px 8px',
                              borderRadius: 4,
                              fontWeight: 600,
                            }}
                          >
                            Cách {item.distance}
                          </span>
                        </div>
                      )}
                    </div>
                  }
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 4 }}>
                    <div>
                      <Text type="secondary" style={{ fontSize: 12 }}>{item.storeName}</Text>
                      <br />
                      <Text type="secondary" style={{ fontSize: 11 }}>📍 {item.storeAddress}</Text>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                      <StarFilled style={{ color: '#faad14', fontSize: 12 }} />
                      <Text strong style={{ fontSize: 12 }}>{item.rating}</Text>
                      <Text type="secondary" style={{ fontSize: 11 }}>({item.reviewsCount})</Text>
                    </div>
                  </div>

                  <Title
                    level={5}
                    style={{ margin: '6px 0 4px 0', fontSize: 15, fontWeight: 700, cursor: 'pointer' }}
                    onClick={() => onSelectBatch && onSelectBatch(item.id)}
                  >
                    {item.title}
                  </Title>
                  <Paragraph
                    ellipsis={{ rows: 2 }}
                    style={{ color: '#8c8c8c', fontSize: 12, marginBottom: 12, minHeight: 36 }}
                  >
                    {item.description}
                  </Paragraph>

                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 8 }}>
                    <div>
                      <span style={{ fontSize: 18, fontWeight: 800, color: '#135200' }}>
                        {formatMoney(item.rescuePrice)}
                      </span>
                      <br />
                      <Text delete type="secondary" style={{ fontSize: 12 }}>
                        {formatMoney(item.originalPrice)}
                      </Text>
                    </div>

                    <Space size={6}>
                      {isAvailable && (
                        <Button
                          type="primary"
                          icon={<ShoppingCartOutlined />}
                          loading={addingId === item.id}
                          onClick={() => handleAddToCart(item)}
                          style={{
                            background: '#135200',
                            borderColor: '#135200',
                            fontWeight: 600,
                            borderRadius: 8,
                          }}
                        >
                          Thêm túi
                        </Button>
                      )}
                      {isSoldOut && (
                        <Button disabled style={{ borderRadius: 8 }}>
                          Hết hàng
                        </Button>
                      )}
                      {isPending && (
                        <Button size="small" style={{ borderRadius: 8 }}>
                          🔔 Nhận chuông báo
                        </Button>
                      )}
                    </Space>
                  </div>
                </Card>
              );
            })}
          </div>
        </div>

        {/* Right Sidebar: Live Cart + Active Order + Trust */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {/* WIDGET 1: LIVE CART */}
          <Card
            title={
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Space>
                  <ShoppingCartOutlined style={{ color: '#52c41a', fontSize: 18 }} />
                  <span style={{ fontWeight: 700 }}>Giỏ Hàng Trực Tiếp</span>
                </Space>
                {cart?.items?.length > 0 && (
                  <Button type="link" danger size="small" onClick={handleClearCart} style={{ padding: 0 }}>
                    Xoá hết
                  </Button>
                )}
              </div>
            }
            style={{ borderRadius: 14, boxShadow: '0 2px 12px rgba(0,0,0,0.05)', border: '1px solid #f0f0f0' }}
            bodyStyle={{ padding: 16 }}
          >
            {cart && cart.items && cart.items.length > 0 ? (
              <div>
                <Alert
                  type="info"
                  showIcon
                  message={
                    <span style={{ fontSize: 12 }}>
                      <strong>{cart.storeName}</strong>: Chỉ chứa túi của 1 quán. Khách nhận trước giờ đóng cửa.
                    </span>
                  }
                  style={{ marginBottom: 12, padding: '6px 10px' }}
                />

                <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 14 }}>
                  {cart.items.map((it) => (
                    <div
                      key={it.batchId}
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        background: '#fafafa',
                        padding: '8px 10px',
                        borderRadius: 8,
                      }}
                    >
                      <div>
                        <Text strong style={{ fontSize: 13 }}>{it.quantity}x {it.title}</Text>
                        <br />
                        <Text type="secondary" style={{ fontSize: 11 }}>
                          Khung giờ: {formatPickupWindow(it.pickupStart, it.pickupEnd)}
                        </Text>
                      </div>
                      <Text strong style={{ color: '#135200' }}>{formatMoney(it.subtotal)}</Text>
                    </div>
                  ))}
                </div>

                <Divider style={{ margin: '10px 0' }} />

                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>Tổng số lượng:</Text>
                  <Text strong>{cart.totalItems} túi</Text>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 16 }}>
                  <div>
                    <Text type="secondary" style={{ fontSize: 13 }}>Tổng tiền thanh toán:</Text>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <span style={{ fontSize: 22, fontWeight: 800, color: '#d4380d' }}>
                      {formatMoney(cart.totalAmount)}
                    </span>
                  </div>
                </div>

                <Button
                  type="primary"
                  block
                  size="large"
                  icon={<CreditCardOutlined />}
                  loading={checkingOut}
                  onClick={handleCheckout}
                  style={{
                    background: '#135200',
                    borderColor: '#135200',
                    fontWeight: 700,
                    height: 46,
                    borderRadius: 8,
                  }}
                >
                  Thanh Toán VNPay ({formatMoney(cart.totalAmount)})
                </Button>

                <div style={{ textAlign: 'center', marginTop: 10 }}>
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    🛡️ Bảo đảm hoàn tiền 100% nếu cửa hàng hết túi đột xuất
                  </Text>
                </div>
              </div>
            ) : (
              <div style={{ textAlign: 'center', padding: '24px 0' }}>
                <ShoppingCartOutlined style={{ fontSize: 36, color: '#d9d9d9', marginBottom: 12 }} />
                <br />
                <Text type="secondary">Chưa có túi nào trong giỏ</Text>
                <br />
                <Text style={{ fontSize: 12, color: '#bfbfbf' }}>Bấm "Thêm túi" ở danh sách bên cạnh để bắt đầu</Text>
              </div>
            )}
          </Card>

          {/* WIDGET 2: ACTIVE ORDER (MÃ NHẬN HÀNG) */}
          {activeOrder && (
            <Card
              style={{
                borderRadius: 14,
                boxShadow: '0 2px 12px rgba(0,0,0,0.05)',
                border: '1px solid #d9f7be',
                background: '#f6ffed',
              }}
              bodyStyle={{ padding: 16 }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <Text strong style={{ fontSize: 13, color: '#389e0d' }}>Đơn hàng đang chờ nhận</Text>
                <Tag color="green">CONFIRMED</Tag>
              </div>

              <div>
                <Text strong style={{ fontSize: 13 }}>Mã: {activeOrder.orderCode}</Text>
                <br />
                <Text type="secondary" style={{ fontSize: 12 }}>{activeOrder.storeName} ({activeOrder.storeAddress})</Text>
              </div>

              <div
                style={{
                  background: '#fff',
                  border: '2px dashed #52c41a',
                  borderRadius: 8,
                  padding: '10px 14px',
                  margin: '12px 0',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <div>
                  <Text type="secondary" style={{ fontSize: 11 }}>MÃ NHẬN TẠI QUẦY:</Text>
                  <div style={{ fontSize: 24, fontWeight: 800, letterSpacing: 4, color: '#135200' }}>
                    {activeOrder.pickupCode}
                  </div>
                </div>
                <Button
                  size="small"
                  icon={<QrcodeOutlined />}
                  onClick={() => setQrModalVisible(true)}
                  style={{ color: '#135200', borderColor: '#135200' }}
                >
                  Mã QR
                </Button>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Button
                  type="link"
                  size="small"
                  style={{ padding: 0 }}
                  onClick={() => {
                    if (onSelectOrder) {
                      onSelectOrder(activeOrder.id || 4);
                    } else if (onGoToOrders) {
                      onGoToOrders();
                    }
                  }}
                >
                  Xem chi tiết đơn & Hóa đơn
                </Button>
                <Button type="link" size="small" style={{ color: '#135200', padding: 0 }}>
                  Xem chỉ đường 🧭
                </Button>
              </div>
            </Card>
          )}

          {/* WIDGET 3: QUALITY COMMITMENT */}
          <Card
            style={{ borderRadius: 14, border: '1px solid #f0f0f0' }}
            bodyStyle={{ padding: 16 }}
          >
            <Space align="start">
              <SafetyCertificateOutlined style={{ fontSize: 20, color: '#52c41a', marginTop: 2 }} />
              <div>
                <Text strong style={{ fontSize: 13 }}>Cam Kết Chất Lượng FoodRescue</Text>
                <Paragraph style={{ color: '#8c8c8c', fontSize: 12, margin: '6px 0 10px 0', lineHeight: 1.5 }}>
                  Các túi đồ ăn được các cửa hàng đóng gói cẩn thận trong vòng 2 giờ trước giờ đóng cửa. Đảm bảo an toàn thực phẩm, không dùng chất bảo quản. Đổi trả hoặc hoàn tiền 100% nếu phát sinh sự cố không đúng cam kết.
                </Paragraph>
                <Space size={8}>
                  <Tag color="cyan">⚡ Chuẩn VSATTP</Tag>
                  <Tag color="green">💰 Hoàn tiền 100%</Tag>
                </Space>
              </div>
            </Space>
          </Card>
        </div>
      </div>

      {/* 4. MAP DISCOVERY SECTION */}
      <div style={{ background: '#fff', borderRadius: 16, padding: 24, marginBottom: 40, border: '1px solid #f0f0f0' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <div>
            <div style={{ fontSize: 11, fontWeight: 700, color: '#135200', letterSpacing: 1 }}>KHÁM PHÁ CÁC ĐIỂM CỨU MÓN</div>
            <Title level={3} style={{ margin: 0, fontWeight: 800 }}>Khám Phá Điểm Giải Cứu Theo Bản Đồ</Title>
            <Text type="secondary">4 điểm giải cứu đang mở bán tại TP.HCM trong bán kính gần bạn</Text>
          </div>
          <Space>
            <Button>Tìm theo khoảng cách</Button>
            <Button type="primary" style={{ background: '#135200', borderColor: '#135200' }} icon={<CompassOutlined />}>
              Mở Bản Đồ Toàn Màn Hình
            </Button>
          </Space>
        </div>

        {/* Map Preview Banner */}
        <div
          style={{
            height: 180,
            background: 'linear-gradient(135deg, #135200 0%, #389e0d 50%, #95de64 100%)',
            borderRadius: 12,
            position: 'relative',
            overflow: 'hidden',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
          }}
        >
          <div style={{ position: 'absolute', inset: 0, opacity: 0.15, background: 'radial-gradient(circle, #fff 10%, transparent 20%)', backgroundSize: '20px 20px' }} />
          <div style={{ display: 'flex', gap: 16, zIndex: 1, flexWrap: 'wrap', justifyContent: 'center' }}>
            <Tag color="gold" style={{ fontSize: 13, padding: '6px 14px', borderRadius: 20 }}>
              📍 Quán An Phát (Q.5 - 850m)
            </Tag>
            <Tag color="blue" style={{ fontSize: 13, padding: '6px 14px', borderRadius: 20 }}>
              📍 Sweet Paris (Q.1 - 2.4km)
            </Tag>
            <Tag color="cyan" style={{ fontSize: 13, padding: '6px 14px', borderRadius: 20 }}>
              📍 Cơm Tấm 365 (Bình Thạnh - 3.1km)
            </Tag>
          </div>
        </div>
      </div>

      {/* 5. HOW IT WORKS: 4 SIMPLE STEPS */}
      <div style={{ textAlign: 'center', marginBottom: 44 }}>
        <div style={{ fontSize: 11, fontWeight: 700, color: '#135200', letterSpacing: 1 }}>QUY TRÌNH TIỆN LỢI & NHANH CHÓNG</div>
        <Title level={2} style={{ margin: '6px 0 16px 0', fontWeight: 800 }}>4 Bước Đơn Giản Để Cứu Món Ngon Tối Nay</Title>
        <Text type="secondary">Dễ dàng chọn món yêu thích chỉ với vài thao tác và lấy trực tiếp tại quầy</Text>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginTop: 24, textAlign: 'left' }}>
          <div style={{ background: '#fff', padding: 20, borderRadius: 12, border: '1px solid #f0f0f0' }}>
            <div style={{ width: 32, height: 32, borderRadius: '50%', background: '#f6ffed', color: '#135200', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, marginBottom: 12 }}>1</div>
            <Text strong style={{ fontSize: 14 }}>Chọn Túi Cứu Trợ</Text>
            <Paragraph style={{ color: '#8c8c8c', fontSize: 12, marginTop: 6 }}>
              Lựa chọn quán, món yêu thích gần bạn với mức giảm 50% - 70% trong ngày.
            </Paragraph>
          </div>

          <div style={{ background: '#fff', padding: 20, borderRadius: 12, border: '1px solid #f0f0f0' }}>
            <div style={{ width: 32, height: 32, borderRadius: '50%', background: '#f6ffed', color: '#135200', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, marginBottom: 12 }}>2</div>
            <Text strong style={{ fontSize: 14 }}>Đặt & Thanh Toán VNPay</Text>
            <Paragraph style={{ color: '#8c8c8c', fontSize: 12, marginTop: 6 }}>
              Thanh toán an toàn trực tuyến qua cổng Sandbox VNPay, hoàn tất đơn ngay lập tức.
            </Paragraph>
          </div>

          <div style={{ background: '#fff', padding: 20, borderRadius: 12, border: '1px solid #f0f0f0' }}>
            <div style={{ width: 32, height: 32, borderRadius: '50%', background: '#f6ffed', color: '#135200', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, marginBottom: 12 }}>3</div>
            <Text strong style={{ fontSize: 14 }}>Nhận Mã Khách Hàng</Text>
            <Paragraph style={{ color: '#8c8c8c', fontSize: 12, marginTop: 6 }}>
              Giữ mã lấy hàng 6 ký tự (VD: H2R9W7) qua tin nhắn app trong vòng thời gian mở bán.
            </Paragraph>
          </div>

          <div style={{ background: '#fff', padding: 20, borderRadius: 12, border: '1px solid #f0f0f0' }}>
            <div style={{ width: 32, height: 32, borderRadius: '50%', background: '#f6ffed', color: '#135200', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, marginBottom: 12 }}>4</div>
            <Text strong style={{ fontSize: 14 }}>Đến Quầy Nhận Món</Text>
            <Paragraph style={{ color: '#8c8c8c', fontSize: 12, marginTop: 6 }}>
              Đến quầy trong khung giờ 20:00 - 22:00, xuất trình mã cho nhân viên để lấy món ngon!
            </Paragraph>
          </div>
        </div>
      </div>

      {/* 6. COMMUNITY & ENVIRONMENTAL IMPACT BANNER */}
      <div
        style={{
          background: '#0d3800',
          color: '#fff',
          borderRadius: 16,
          padding: '24px 32px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 44,
          flexWrap: 'wrap',
          gap: 20,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <div style={{ background: '#1b5e20', padding: 12, borderRadius: '50%', fontSize: 24 }}>🌱</div>
          <div>
            <Title level={4} style={{ color: '#fff', margin: 0 }}>Tác Động Môi Trường Cộng Đồng</Title>
            <Text style={{ color: '#a5d6a7', fontSize: 13 }}>Cùng chung tay giảm thiểu lãng phí thực phẩm mỗi ngày tại TP.HCM</Text>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 36 }}>
          <div>
            <div style={{ fontSize: 26, fontWeight: 800, color: '#d9f7be' }}>1,420+</div>
            <Text style={{ color: '#a5d6a7', fontSize: 12 }}>Bữa ăn ngon đã cứu thành công</Text>
          </div>
          <div>
            <div style={{ fontSize: 26, fontWeight: 800, color: '#d9f7be' }}>2.8 Tấn</div>
            <Text style={{ color: '#a5d6a7', fontSize: 12 }}>Lượng CO2 đã giảm thiểu</Text>
          </div>
        </div>
      </div>

      {/* 7. DETAILED FOOTER */}
      <div style={{ background: '#fff', borderTop: '1px solid #f0f0f0', padding: '36px 0 20px 0' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr 1fr 1fr', gap: 28, marginBottom: 24 }}>
          <div>
            <Title level={4} style={{ color: '#135200', margin: '0 0 10px 0' }}>🥑 FoodRescue</Title>
            <Paragraph style={{ color: '#8c8c8c', fontSize: 12, lineHeight: 1.6 }}>
              Nền tảng kết nối các tiệm bánh, nhà hàng và quán ăn để người tiêu dùng mua lại thực phẩm cuối ngày thơm ngon với mức giảm 50% - 70%.
            </Paragraph>
            <Tag color="green">🛡️ BẢO TRỢ THANH TOÁN BỞI VNPAY SANDBOX</Tag>
          </div>

          <div>
            <Text strong style={{ fontSize: 13 }}>Danh Mục Nổi Bật</Text>
            <div style={{ marginTop: 10, display: 'flex', flexDirection: 'column', gap: 6 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>Bánh Mì Tươi An Phát (Q.5)</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>Sweet Paris Coffee & Bakery (Q.1)</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>Cơm Tấm Truyền Thống 365 (Bình Thạnh)</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>Rau Sạch GreenMart (Đang duyệt)</Text>
            </div>
          </div>

          <div>
            <Text strong style={{ fontSize: 13 }}>Đối Tác & Chính Sách</Text>
            <div style={{ marginTop: 10, display: 'flex', flexDirection: 'column', gap: 6 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>Dành Cho Đối Tác Quán Ăn / Bakery</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>Tiêu Chuẩn An Toàn Thực Phẩm</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>Chính Sách Thanh Toán & Hoàn Tiền VNPay</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>Điều Khoản Sử Dụng & Bảo Mật</Text>
            </div>
          </div>

          <div>
            <Text strong style={{ fontSize: 13 }}>Hỗ Trợ & Hoạt Động</Text>
            <div style={{ marginTop: 10, display: 'flex', flexDirection: 'column', gap: 6 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>⏰ Khung giờ hoạt động: 20:00 - 22:00 mỗi ngày</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>📞 Hotline hỗ trợ: 1900 6868 (8h - 22h)</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>📧 support@foodrescue.vn</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>📍 Văn phòng điều hành: Quận 1, TP. Hồ Chí Minh</Text>
            </div>
          </div>
        </div>

        <Divider style={{ margin: '16px 0' }} />
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            © 2026 FoodRescue Vietnam · Đồ án môn học J2EE · Hệ thống giải cứu thực phẩm cuối ngày.
          </Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Thanh toán an toàn qua VNPay QR, ATM & Thẻ Quốc Tế
          </Text>
        </div>
      </div>

      {/* MODAL QR CODE */}
      <Modal
        title="Mã nhận hàng tại quầy"
        open={qrModalVisible}
        onCancel={() => setQrModalVisible(false)}
        footer={null}
        destroyOnClose
      >
        {activeOrder && (
          <div style={{ textAlign: 'center', padding: '16px 0' }}>
            <Text type="secondary">Cửa hàng: </Text>
            <Title level={4} style={{ margin: '4px 0' }}>{activeOrder.storeName}</Title>
            <Text type="secondary">{activeOrder.storeAddress}</Text>

            <div style={{ margin: '20px 0', padding: 16, background: '#f6ffed', border: '2px dashed #b7eb8f', borderRadius: 8 }}>
              <Text type="secondary">Đưa mã hoặc QR cho nhân viên quầy quét:</Text>
              <Title level={1} style={{ margin: '8px 0', letterSpacing: 6, color: '#135200' }}>
                {activeOrder.pickupCode}
              </Title>
              <div style={{ display: 'flex', justifyContent: 'center', marginTop: 12 }}>
                <QRCodeSVG value={activeOrder.pickupCode} size={180} />
              </div>
            </div>

            <Alert
              type="success"
              showIcon
              message={`Khung giờ nhận: ${formatPickupWindow(activeOrder.pickupStart, activeOrder.pickupEnd)}`}
              description="Vui lòng đến quầy đúng khung giờ để nhận túi bánh tươi ngon nhất!"
            />
          </div>
        )}
      </Modal>
    </div>
  );
}
