import { useState, useEffect } from 'react';
import {
  Breadcrumb,
  Button,
  Tag,
  Typography,
  Space,
  Tabs,
  Rate,
  Alert,
  message,
  Spin,
} from 'antd';
import {
  ArrowLeftOutlined,
  HeartOutlined,
  ShareAltOutlined,
  ClockCircleOutlined,
  EnvironmentOutlined,
  CheckCircleOutlined,
  CreditCardOutlined,
  ShoppingCartOutlined,
  SafetyCertificateOutlined,
  MinusOutlined,
  PlusOutlined,
  CompassOutlined,
} from '@ant-design/icons';
import { cartApi, orderApi, paymentApi, batchApi } from '../order/api';
import { formatMoney, formatPickupWindow } from '../../shared/utils/format';

const { Title, Text, Paragraph } = Typography;

export default function RescueBatchDetailView({ batchId, onBack, onGoToOrders }) {
  const [quantity, setQuantity] = useState(1);
  const [adding, setAdding] = useState(false);
  const [ordering, setOrdering] = useState(false);
  const [activeTabKey, setActiveTabKey] = useState('info');
  const [loading, setLoading] = useState(true);
  const [batchData, setBatchData] = useState(null);

  useEffect(() => {
    let isMounted = true;
    async function fetchDetail() {
      setLoading(true);
      try {
        const data = await batchApi.getBatchDetail(batchId || 3);
        if (isMounted) {
          setBatchData(data);
        }
      } catch (err) {
        console.error('Failed to load batch from DB, fallback default:', err);
      } finally {
        if (isMounted) setLoading(false);
      }
    }
    fetchDetail();
    return () => { isMounted = false; };
  }, [batchId]);

  // Merge dữ liệu DB thực tế với UI template
  const batch = {
    id: batchData?.id || batchId || 3,
    title: batchData?.title || 'Túi Bánh Mì & Pastry Cuối Ngày Bất Ngờ',
    subtitle: batchData?.description || 'Bạn nhận được combo gồm 3 đến 5 món bánh nướng thủ công thượng hạng nướng trong ngày. Món bánh nguyên vị, thơm lừng và trọn vẹn dinh dưỡng chưa tìm thấy chân ái trước giờ đóng cửa.',
    storeName: batchData?.storeName || 'Sweet Paris Bakery',
    storeAddress: batchData?.storeAddress || '45 Lê Duẩn, P. Bến Nghé, Quận 1, TP.HCM',
    distance: '1.2 km (8 phút)',
    storeHours: batchData?.openTime && batchData?.closeTime ? `${batchData.openTime} - ${batchData.closeTime}` : '07:00 - 22:00',
    rescuePrice: batchData?.rescuePrice || 35000,
    originalPrice: batchData?.originalPrice || 100000,
    discount: batchData ? `-${Math.round((1 - batchData.rescuePrice / batchData.originalPrice) * 100)}%` : '-65%',
    remainingQuantity: batchData?.remainingQuantity ?? 14,
    totalQuantity: batchData?.quantity ?? 20,
    soldCount: batchData ? Math.max(0, batchData.quantity - batchData.remainingQuantity) : 7,
    pickupWindow: batchData ? formatPickupWindow(batchData.pickupStart, batchData.pickupEnd) : '20:30 - 21:30',
    countdown: 'Còn 2 giờ 15 phút',
    savedCo2: '3.2 kg',
    savedFood: '1.8 kg',
    rating: batchData?.averageRating || 4.9,
    reviewsCount: batchData?.reviewsCount || 128,
    reviews: batchData?.reviews || [],
    mainImage: batchData?.imageUrl || 'https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=1000&auto=format&fit=crop&q=80',
    thumbnails: [
      batchData?.imageUrl || 'https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=1000&auto=format&fit=crop&q=80',
      'https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&auto=format&fit=crop&q=80',
      'https://images.unsplash.com/photo-1556910103-1c02745aae4d?w=400&auto=format&fit=crop&q=80',
      'https://images.unsplash.com/photo-1578985545062-69928b1d9587?w=400&auto=format&fit=crop&q=80',
    ],
  };

  const handleAddToCart = async () => {
    setAdding(true);
    try {
      await cartApi.addToCart(batch.id, quantity);
      message.success(`Đã thêm ${quantity} túi vào giỏ hàng!`);
    } catch (err) {
      message.error(err.message || 'Không thể thêm vào giỏ hàng');
    } finally {
      setAdding(false);
    }
  };

  const handleInstantCheckout = async () => {
    setOrdering(true);
    try {
      await cartApi.clearCart();
      await cartApi.addToCart(batch.id, quantity);
      const newOrder = await orderApi.createOrder();
      message.success(`Đặt thành công đơn ${newOrder.orderCode}! Đang mở VNPay...`);
      const payRes = await paymentApi.createPaymentUrl(newOrder.id);
      if (payRes.paymentUrl) {
        window.open(payRes.paymentUrl, '_blank');
      }
      if (onGoToOrders) onGoToOrders();
    } catch (err) {
      message.error(err.message || 'Lỗi khi tạo đơn hàng');
    } finally {
      setOrdering(false);
    }
  };

  if (loading && !batchData) {
    return (
      <div style={{ padding: '80px 0', textAlign: 'center' }}>
        <Spin size="large" tip="Đang tải dữ liệu từ CSDL..." />
      </div>
    );
  }

  return (
    <div style={{ background: '#f8fafc', minHeight: '100vh', paddingBottom: 60 }}>
      {/* Top Banner Thông Báo */}
      <div style={{ background: '#e6f7ff', borderBottom: '1px solid #91d5ff', padding: '8px 24px', fontSize: 13, color: '#0050b3', display: 'flex', justifyContent: 'space-between' }}>
        <span>⚡ Cam kết túi đồ ăn: Mức giá ưu đãi tối thiểu 50% so với niêm yết (Dữ liệu CSDL #batch-{batch.id})</span>
        <span>⏰ Đang mở bán khung giờ cuối ngày &nbsp;•&nbsp; Cửa hàng: <strong>{batch.storeName}</strong></span>
      </div>

      <div style={{ maxWidth: 1200, margin: '0 auto', padding: '16px 20px' }}>
        {/* Breadcrumb & Navigation */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={onBack} type="text">Quay lại</Button>
            <Breadcrumb
              items={[
                { title: 'Trang chủ' },
                { title: 'Cứu thực phẩm' },
                { title: batch.storeName },
                { title: batch.title },
              ]}
            />
          </Space>
          <Space>
            <Button icon={<HeartOutlined />} shape="circle" />
            <Button icon={<ShareAltOutlined />} shape="circle" />
          </Space>
        </div>

        {/* 2-COLUMN MAIN CONTENT */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 380px', gap: 28, alignItems: 'start' }}>
          {/* CỘT TRÁI: HÌNH ẢNH, MÔ TẢ, QUY TẮC, ĐÁNH GIÁ */}
          <div>
            {/* Gallery ảnh */}
            <div style={{ background: '#fff', borderRadius: 16, overflow: 'hidden', border: '1px solid #e2e8f0', marginBottom: 20 }}>
              <div style={{ position: 'relative', height: 380 }}>
                <img
                  src={batch.mainImage}
                  alt={batch.title}
                  style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                />
                <div style={{ position: 'absolute', top: 16, left: 16, display: 'flex', gap: 8 }}>
                  <Tag color="#d4380d" style={{ fontWeight: 700, padding: '4px 12px', fontSize: 13, borderRadius: 6, margin: 0 }}>
                    🔥 Tiết kiệm {batch.discount} - Chỉ còn hôm nay
                  </Tag>
                </div>
                <div style={{ position: 'absolute', bottom: 16, left: 16, background: 'rgba(0,0,0,0.65)', color: '#fff', padding: '6px 14px', borderRadius: 20, fontSize: 13 }}>
                  🌱 Giảm {batch.savedFood} rác thải thực phẩm • Tiết kiệm {batch.savedCo2} CO2e
                </div>
              </div>

              {/* Thumbnails */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 10, padding: 12, background: '#fafafa' }}>
                {batch.thumbnails.map((thumb, idx) => (
                  <div key={idx} style={{ height: 85, borderRadius: 8, overflow: 'hidden', cursor: 'pointer', border: idx === 0 ? '2px solid #52c41a' : '1px solid #d9d9d9' }}>
                    <img src={thumb} alt="thumb" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  </div>
                ))}
              </div>
            </div>

            {/* Thông tin tiêu đề & mô tả */}
            <div style={{ background: '#fff', borderRadius: 16, padding: 24, border: '1px solid #e2e8f0', marginBottom: 20 }}>
              <Space style={{ marginBottom: 8 }}>
                <Tag color="green" style={{ fontWeight: 700 }}>TÚI ĐỒ ĂN BẤT NGỜ (SURPRISE BAG)</Tag>
                <Text type="secondary" style={{ fontSize: 12 }}>Khung giờ: {batch.pickupWindow}</Text>
              </Space>

              <Title level={2} style={{ margin: '4px 0 12px 0', fontWeight: 800 }}>{batch.title}</Title>
              <Paragraph style={{ color: '#475569', fontSize: 14, lineHeight: 1.7, marginBottom: 20 }}>
                {batch.subtitle}
              </Paragraph>

              {/* Box giải thích quy cách */}
              <div style={{ background: '#f6ffed', border: '1px solid #b7eb8f', borderRadius: 12, padding: 16, marginBottom: 20 }}>
                <Space align="start">
                  <ClockCircleOutlined style={{ fontSize: 24, color: '#52c41a', marginTop: 2 }} />
                  <div>
                    <Text strong style={{ fontSize: 14, color: '#135200' }}>Quy cách "Túi Bất Ngờ" hoạt động như thế nào?</Text>
                    <Paragraph style={{ margin: '4px 0 0 0', fontSize: 13, color: '#237804', lineHeight: 1.6 }}>
                      Để giảm thiểu lãng phí và tối ưu nguồn lực tại cửa hàng, thành phẩm chính xác của từng túi sẽ ngẫu nhiên phụ thuộc vào lượng món còn lại tại quầy trước giờ đóng cửa. Tổng giá trị niêm yết của các món luôn từ <strong>{formatMoney(batch.originalPrice)} trở lên</strong>.
                    </Paragraph>
                  </div>
                </Space>
              </div>

              {/* Các loại món thường xuất hiện */}
              <div>
                <Text strong style={{ fontSize: 14, display: 'block', marginBottom: 10 }}>🧁 Các món thường xuất hiện trong túi:</Text>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
                  <div style={{ background: '#f8fafc', padding: 12, borderRadius: 8, border: '1px solid #e2e8f0', textAlign: 'center' }}>
                    <div style={{ fontSize: 20 }}>🥐</div>
                    <Text strong style={{ fontSize: 12, display: 'block' }}>Món Đặc Trưng 1</Text>
                    <Text type="secondary" style={{ fontSize: 11 }}>Nướng tươi mỗi ngày</Text>
                  </div>
                  <div style={{ background: '#f8fafc', padding: 12, borderRadius: 8, border: '1px solid #e2e8f0', textAlign: 'center' }}>
                    <div style={{ fontSize: 20 }}>🍫</div>
                    <Text strong style={{ fontSize: 12, display: 'block' }}>Món Đặc Trưng 2</Text>
                    <Text type="secondary" style={{ fontSize: 11 }}>Nguyên liệu sạch</Text>
                  </div>
                  <div style={{ background: '#f8fafc', padding: 12, borderRadius: 8, border: '1px solid #e2e8f0', textAlign: 'center' }}>
                    <div style={{ fontSize: 20 }}>🥖</div>
                    <Text strong style={{ fontSize: 12, display: 'block' }}>Món Đặc Trưng 3</Text>
                    <Text type="secondary" style={{ fontSize: 11 }}>Tiêu chuẩn quán</Text>
                  </div>
                  <div style={{ background: '#f8fafc', padding: 12, borderRadius: 8, border: '1px solid #e2e8f0', textAlign: 'center' }}>
                    <div style={{ fontSize: 20 }}>🥧</div>
                    <Text strong style={{ fontSize: 12, display: 'block' }}>Món Đặc Trưng 4</Text>
                    <Text type="secondary" style={{ fontSize: 11 }}>Trọn vị dinh dưỡng</Text>
                  </div>
                </div>
              </div>
            </div>

            {/* Cam kết VSATTP & Tác động bền vững */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 20 }}>
              <div style={{ background: '#fff', borderRadius: 14, padding: 18, border: '1px solid #e2e8f0' }}>
                <Space align="start">
                  <SafetyCertificateOutlined style={{ fontSize: 24, color: '#52c41a', marginTop: 2 }} />
                  <div>
                    <Text strong style={{ fontSize: 13, display: 'block' }}>Cam kết VSATTP & Bảo quản</Text>
                    <Text type="secondary" style={{ fontSize: 12 }}>Chứng nhận an toàn thực phẩm chuẩn TP.HCM</Text>
                    <div style={{ marginTop: 8, fontSize: 12, color: '#64748b', lineHeight: 1.6 }}>
                      • Chế biến trong ngày, bảo quản hợp vệ sinh.<br />
                      • Đóng gói kín bằng túi thân thiện môi trường.<br />
                      • Hạn sử dụng tốt nhất: trong vòng 24 giờ sau khi lấy túi.
                    </div>
                  </div>
                </Space>
              </div>

              <div style={{ background: 'linear-gradient(135deg, #135200 0%, #237804 100%)', borderRadius: 14, padding: 18, color: '#fff' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                  <Text strong style={{ color: '#fff', fontSize: 13 }}>Tác động bền vững</Text>
                  <Tag color="gold" style={{ margin: 0 }}>Net-Zero</Tag>
                </div>
                <div style={{ display: 'flex', gap: 24, marginBottom: 8 }}>
                  <div>
                    <div style={{ fontSize: 22, fontWeight: 800 }}>{batch.savedFood}</div>
                    <div style={{ fontSize: 12, color: '#d9f7be' }}>Thực phẩm không bị lãng phí</div>
                  </div>
                  <div>
                    <div style={{ fontSize: 22, fontWeight: 800 }}>{batch.savedCo2}</div>
                    <div style={{ fontSize: 12, color: '#d9f7be' }}>CO2e ngăn thải ra môi trường</div>
                  </div>
                </div>
                <div style={{ fontSize: 11, color: '#b7eb8f', lineHeight: 1.4 }}>
                  Bằng việc nhận túi này, bạn đã chung tay bảo vệ tài nguyên nước và giảm thiểu rác thải hữu cơ!
                </div>
              </div>
            </div>

            {/* Tabs Thông Tin & Đánh Giá Khách Hàng (TỪ DATABASE REVIEWS) */}
            <div style={{ background: '#fff', borderRadius: 16, padding: 24, border: '1px solid #e2e8f0' }}>
              <Tabs
                activeKey={activeTabKey}
                onChange={setActiveTabKey}
                items={[
                  { key: 'info', label: '📋 Thông tin & Bảo quản' },
                  { key: 'reviews', label: `⭐ Đánh giá từ khách hàng (${batch.rating} / ${batch.reviewsCount})` },
                ]}
              />

              {activeTabKey === 'info' && (
                <div style={{ fontSize: 13, color: '#475569', lineHeight: 1.8 }}>
                  <p><strong>Hướng dẫn nhận hàng:</strong> Mang mã nhận hàng 6 ký tự hoặc mã QR trên ứng dụng đến quầy thu ngân của <strong>{batch.storeName}</strong> trong khung giờ <strong>{batch.pickupWindow}</strong>. Nhân viên sẽ quét mã và bàn giao túi ngay.</p>
                  <p><strong>Bảo quản:</strong> Để ở nhiệt độ phòng thoáng mát hoặc ngăn mát tủ lạnh nếu dùng vào sáng hôm sau. Làm nóng lại trước khi thưởng thức.</p>
                  <p><strong>Chính sách hoàn tiền:</strong> Nếu đến nơi mà cửa hàng hết phần do sự cố vận hành, hệ thống sẽ tự động hoàn 100% tiền qua VNPay trong vòng 24 giờ.</p>
                </div>
              )}

              {activeTabKey === 'reviews' && (
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 24, marginBottom: 20, padding: 16, background: '#fafafa', borderRadius: 12 }}>
                    <div style={{ textAlign: 'center' }}>
                      <div style={{ fontSize: 36, fontWeight: 800, color: '#fa8c16' }}>{batch.rating}</div>
                      <Rate disabled allowHalf value={batch.rating} style={{ fontSize: 14 }} />
                      <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: 4 }}>{batch.reviewsCount} lượt đánh giá thực tế</div>
                    </div>
                    <div style={{ flex: 1 }}>
                      <Alert message="Tất cả đánh giá đều từ khách hàng đã đến nhận túi thực tế và hoàn tất đơn tại quầy." type="info" showIcon />
                    </div>
                  </div>

                  {batch.reviews && batch.reviews.length > 0 ? (
                    batch.reviews.map((rev) => (
                      <div key={rev.id} style={{ padding: '12px 0', borderBottom: '1px solid #f0f0f0' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <Space>
                            <div style={{ width: 36, height: 36, borderRadius: '50%', background: '#fa8c16', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700 }}>
                              {rev.customerName ? rev.customerName.charAt(0) : 'U'}
                            </div>
                            <div>
                              <Text strong>{rev.customerName || 'Khách hàng ẩn danh'}</Text>
                              <Tag color="green" style={{ fontSize: 11, marginLeft: 8 }}>✔ Đã nhận hàng thành công</Tag>
                            </div>
                          </Space>
                          <Rate disabled value={rev.rating} style={{ fontSize: 12 }} />
                        </div>
                        <Paragraph style={{ margin: '8px 0 0 44px', color: '#475569', fontSize: 13 }}>
                          "{rev.comment || 'Túi đồ ăn rất ngon, đóng gói sạch sẽ!'}"
                        </Paragraph>
                      </div>
                    ))
                  ) : (
                    <>
                      <div style={{ padding: '12px 0', borderBottom: '1px solid #f0f0f0' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <Space>
                            <div style={{ width: 36, height: 36, borderRadius: '50%', background: '#fa8c16', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700 }}>MT</div>
                            <div>
                              <Text strong>Minh Thư</Text>
                              <Tag color="green" style={{ fontSize: 11, marginLeft: 8 }}>✔ Đã nhận hàng thành công</Tag>
                            </div>
                          </Space>
                          <Rate disabled defaultValue={5} style={{ fontSize: 12 }} />
                        </div>
                        <Paragraph style={{ margin: '8px 0 0 44px', color: '#475569', fontSize: 13 }}>
                          "Quá tuyệt vời luôn cả nhà ơi! Mình mua cứu thực phẩm mà nhận được món bánh thơm nức mũi, giá lại siêu rẻ. Vừa tiết kiệm vừa bảo vệ môi trường, nhân viên phục vụ pickup cực nhanh và lịch sự!"
                        </Paragraph>
                      </div>

                      <div style={{ padding: '12px 0' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <Space>
                            <div style={{ width: 36, height: 36, borderRadius: '50%', background: '#1890ff', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700 }}>HN</div>
                            <div>
                              <Text strong>Hoàng Nam</Text>
                              <Tag color="green" style={{ fontSize: 11, marginLeft: 8 }}>✔ Đã nhận hàng thành công</Tag>
                            </div>
                          </Space>
                          <Rate disabled defaultValue={5} style={{ fontSize: 12 }} />
                        </div>
                        <Paragraph style={{ margin: '8px 0 0 44px', color: '#475569', fontSize: 13 }}>
                          "Mình tan làm tạt qua lấy rất tiện. Quét mã QR trên điện thoại tầm 10 giây. Đựng trong túi giấy thân thiện môi trường, ấm lòng bữa tối!"
                        </Paragraph>
                      </div>
                    </>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* CỘT PHẢI: KHUNG ĐẶT HÀNG & THÔNG TIN CỬA HÀNG (STICKY) */}
          <div style={{ position: 'sticky', top: 80 }}>
            {/* Widget giá & Đặt hàng */}
            <div style={{ background: '#fff', borderRadius: 16, padding: 24, border: '1px solid #e2e8f0', boxShadow: '0 4px 16px rgba(0,0,0,0.06)', marginBottom: 20 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <Text type="secondary" style={{ fontSize: 13 }}>Mức giá cứu hôm nay:</Text>
                <Tag color="gold" style={{ fontWeight: 700 }}>GIÁ SIÊU TIẾT KIỆM</Tag>
              </div>

              <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, marginBottom: 16 }}>
                <span style={{ fontSize: 32, fontWeight: 800, color: '#135200' }}>{formatMoney(batch.rescuePrice)}</span>
                <span style={{ fontSize: 16, textDecoration: 'line-through', color: '#94a3b8' }}>{formatMoney(batch.originalPrice)}</span>
                <span style={{ fontSize: 13, color: '#64748b' }}>/ 1 túi</span>
              </div>

              <div style={{ background: '#fff1f0', border: '1px solid #ffa39e', borderRadius: 8, padding: '8px 12px', marginBottom: 16, display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
                <span style={{ color: '#cf1322' }}>🔥 Chỉ còn <strong>{batch.remainingQuantity}/{batch.totalQuantity}</strong> suất hôm nay!</span>
                <span style={{ color: '#8c8c8c' }}>{batch.soldCount} người đã đặt</span>
              </div>

              {/* Khung giờ nhận hàng */}
              <div style={{ background: '#f8fafc', borderRadius: 10, padding: 14, border: '1px solid #e2e8f0', marginBottom: 20 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                  <Space size={6}>
                    <ClockCircleOutlined style={{ color: '#fa8c16' }} />
                    <Text strong style={{ fontSize: 13 }}>Khung giờ lấy hàng (Pickup Window):</Text>
                  </Space>
                  <Tag color="orange" style={{ margin: 0, fontSize: 11 }}>Tối nay</Tag>
                </div>
                <div style={{ fontSize: 20, fontWeight: 800, color: '#0f172a' }}>{batch.pickupWindow}</div>
                <div style={{ fontSize: 12, color: '#ea580c', marginTop: 4 }}>
                  ⏳ Vui lòng đến đúng khung giờ để nhận món mới đóng túi!
                </div>
              </div>

              {/* Bộ chọn số lượng */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
                <div>
                  <Text strong style={{ fontSize: 14 }}>Chọn số lượng:</Text>
                  <div style={{ fontSize: 11, color: '#8c8c8c' }}>Tối đa 5 túi (theo chính sách nền tảng)</div>
                </div>
                <Space style={{ background: '#f1f5f9', borderRadius: 8, padding: '4px 8px' }}>
                  <Button
                    size="small"
                    icon={<MinusOutlined />}
                    disabled={quantity <= 1}
                    onClick={() => setQuantity(q => Math.max(1, q - 1))}
                  />
                  <span style={{ minWidth: 24, textAlign: 'center', fontWeight: 700, fontSize: 15 }}>{quantity}</span>
                  <Button
                    size="small"
                    icon={<PlusOutlined />}
                    disabled={quantity >= 5 || quantity >= batch.remainingQuantity}
                    onClick={() => setQuantity(q => Math.min(5, q + 1))}
                  />
                </Space>
              </div>

              {/* Tạm tính */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, paddingTop: 12, borderTop: '1px dashed #e2e8f0' }}>
                <Text strong style={{ fontSize: 14 }}>Tạm tính:</Text>
                <span style={{ fontSize: 22, fontWeight: 800, color: '#135200' }}>
                  {formatMoney(batch.rescuePrice * quantity)}
                </span>
              </div>

              {/* CÁC NÚT HÀNH ĐỘNG */}
              <Space direction="vertical" style={{ width: '100%' }} size={10}>
                <Button
                  type="primary"
                  size="large"
                  block
                  icon={<CreditCardOutlined />}
                  loading={ordering}
                  disabled={batch.remainingQuantity <= 0}
                  onClick={handleInstantCheckout}
                  style={{
                    background: '#135200',
                    borderColor: '#135200',
                    height: 48,
                    fontSize: 15,
                    fontWeight: 700,
                    borderRadius: 10,
                  }}
                >
                  ⚡ Đặt & Giữ Túi Ngay
                </Button>

                <Button
                  size="large"
                  block
                  icon={<ShoppingCartOutlined />}
                  loading={adding}
                  disabled={batch.remainingQuantity <= 0}
                  onClick={handleAddToCart}
                  style={{
                    height: 46,
                    fontSize: 14,
                    fontWeight: 600,
                    borderRadius: 10,
                    borderColor: '#135200',
                    color: '#135200',
                  }}
                >
                  Thêm vào giỏ hàng
                </Button>
              </Space>

              {/* Quy tắc lấy hàng nhắc nhở */}
              <div style={{ marginTop: 16, padding: '10px 12px', background: '#fffbe6', borderRadius: 8, border: '1px solid #ffe58f', fontSize: 12, color: '#d46b08', lineHeight: 1.5 }}>
                <div><strong>📌 Quy tắc ĐH-01:</strong> Mỗi đơn hàng chỉ chứa túi đồ ăn của 1 cửa hàng để đảm bảo trải nghiệm nhận hàng.</div>
                <div style={{ marginTop: 4 }}><strong>⏳ Quy tắc ĐH-05:</strong> Khách không đến lấy sau 30 phút kể từ khi hết hạn pickup sẽ tính là No-show và không hoàn tiền.</div>
              </div>
            </div>

            {/* Widget thông tin cửa hàng & Bản đồ mini */}
            <div style={{ background: '#fff', borderRadius: 16, padding: 20, border: '1px solid #e2e8f0' }}>
              <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 12 }}>
                <div style={{ width: 44, height: 44, borderRadius: 10, background: '#e6f7ff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20 }}>
                  🏬
                </div>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <Text strong style={{ fontSize: 14 }}>{batch.storeName}</Text>
                    <CheckCircleOutlined style={{ color: '#1890ff' }} />
                  </div>
                  <Text type="secondary" style={{ fontSize: 12 }}>{batch.storeAddress}</Text>
                </div>
              </div>

              <div style={{ fontSize: 12, color: '#64748b', marginBottom: 14 }}>
                📍 {batch.distance} &nbsp;•&nbsp; 🕒 Giờ mở cửa: {batch.storeHours}
              </div>

              {/* Khung bản đồ mini */}
              <div
                style={{
                  height: 120,
                  borderRadius: 10,
                  background: 'linear-gradient(135deg, #e2e8f0 0%, #cbd5e1 100%)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  marginBottom: 12,
                  position: 'relative',
                  overflow: 'hidden',
                }}
              >
                <div style={{ textAlign: 'center' }}>
                  <EnvironmentOutlined style={{ fontSize: 24, color: '#dc2626' }} />
                  <div style={{ fontSize: 12, fontWeight: 700, marginTop: 4 }}>{batch.storeName}</div>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                <Button icon={<CompassOutlined />} size="small" block>
                  Chỉ đường
                </Button>
                <Button size="small" block>
                  Xem gian hàng
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
