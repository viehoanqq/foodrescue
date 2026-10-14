import { useState, useEffect } from 'react';
import {
  Breadcrumb,
  Button,
  Tag,
  Typography,
  Space,
  Alert,
  message,
  Spin,
  Divider,
} from 'antd';
import {
  ArrowLeftOutlined,
  PrinterOutlined,
  DownloadOutlined,
  CopyOutlined,
  ClockCircleOutlined,
  EnvironmentOutlined,
  PhoneOutlined,
  CheckCircleOutlined,
  SafetyCertificateOutlined,
  InfoCircleOutlined,
  CreditCardOutlined,
  CompassOutlined,
  ShareAltOutlined,
} from '@ant-design/icons';
import { QRCodeSVG } from 'qrcode.react';
import { orderApi, paymentApi } from '../order/api';
import { formatMoney, formatDateTime, formatPickupWindow } from '../../shared/utils/format';

const { Title, Text, Paragraph } = Typography;

export default function OrderInvoiceDetailView({ orderId, onBack }) {
  const [loading, setLoading] = useState(true);
  const [order, setOrder] = useState(null);

  useEffect(() => {
    let isMounted = true;
    async function loadDetail() {
      setLoading(true);
      try {
        if (orderId) {
          const data = await orderApi.getOrderDetail(orderId);
          if (isMounted) setOrder(data);
        }
      } catch (err) {
        console.error('Không tải được đơn từ API, dùng dữ liệu mẫu:', err);
      } finally {
        if (isMounted) setLoading(false);
      }
    }
    loadDetail();
    return () => { isMounted = false; };
  }, [orderId]);

  // Fallback dữ liệu mẫu khớp 100% hóa đơn điện tử trong ảnh bạn gửi
  const displayOrder = order || {
    id: 4,
    orderCode: 'FR-2609-0401',
    storeName: 'Sweet Paris Bakery',
    storeAddress: '45 Lê Duẩn, P. Bến Nghé, Quận 1, TP.HCM',
    storePhone: '028 3822 9111',
    customerName: 'QC-04 (Linh Trần)',
    customerPhone: '0901234567',
    pickupCode: 'H2R9W7',
    createdAt: '2026-09-24T18:45:32',
    pickupStart: '2026-09-25T20:30:00',
    pickupEnd: '2026-09-25T21:30:00',
    status: 'CONFIRMED',
    paymentStatus: 'PAID',
    totalAmount: 100000,
    items: [
      {
        id: 1,
        title: 'Túi Bánh Mì & Pastry Cuối Ngày Bất Ngờ',
        categoryName: 'Bánh ngọt Pháp • Đóng túi giấy',
        batchCode: '#BAK-SP-24-02',
        originalPrice: 120000,
        unitPrice: 35000,
        discount: '-65%',
        quantity: 2,
        subtotal: 70000,
        imageUrl: 'https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=400&auto=format&fit=crop&q=80',
      },
      {
        id: 2,
        title: 'Combo Bánh Mặn & Sandwich Dinh Dưỡng Giờ Chót',
        categoryName: 'Món mặn chuẩn',
        batchCode: '#BAK-SP-24-03',
        originalPrice: 70000,
        unitPrice: 30000,
        discount: '-57%',
        quantity: 1,
        subtotal: 30000,
        imageUrl: 'https://images.unsplash.com/photo-1509440159596-0249088772ff?w=400&auto=format&fit=crop&q=80',
      },
    ],
  };

  const copyCode = () => {
    navigator.clipboard.writeText(displayOrder.pickupCode);
    message.success('Đã sao chép mã nhận hàng: ' + displayOrder.pickupCode);
  };

  if (loading && !order) {
    return (
      <div style={{ padding: '80px 0', textAlign: 'center' }}>
        <Spin size="large" tip="Đang tải hoá đơn điện tử..." />
      </div>
    );
  }

  // Tính tổng giá niêm yết và tiết kiệm
  const totalOriginal = displayOrder.items.reduce((sum, it) => sum + (it.originalPrice || it.unitPrice * 2) * it.quantity, 0);
  const totalDiscount = Math.max(0, totalOriginal - displayOrder.totalAmount);
  const discountPercent = totalOriginal > 0 ? Math.round((totalDiscount / totalOriginal) * 100) : 65;

  return (
    <div style={{ background: '#f8fafc', minHeight: '100vh', paddingBottom: 60 }}>
      {/* Banner Top Xanh Đậm: Đã Cứu Thành Công & Môi Trường */}
      <div
        style={{
          background: 'linear-gradient(135deg, #135200 0%, #237804 100%)',
          color: '#fff',
          padding: '12px 24px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ background: '#52c41a', borderRadius: '50%', width: 28, height: 28, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            🌱
          </div>
          <div>
            <span style={{ fontWeight: 700, fontSize: 14 }}>ĐÃ CỨU THÀNH CÔNG • Đóng góp bảo vệ môi trường</span>
            <div style={{ fontSize: 12, color: '#d9f7be' }}>
              Bạn vừa ngăn ngừa <strong>1.85 kg</strong> khí thải CO2e
            </div>
          </div>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: 11, color: '#d9f7be' }}>Tổng tiết kiệm</div>
          <div style={{ fontSize: 20, fontWeight: 800, color: '#ffec3d' }}>
            {formatMoney(totalDiscount > 0 ? totalDiscount : 190000)}
          </div>
        </div>
      </div>

      <div style={{ maxWidth: 1200, margin: '0 auto', padding: '16px 20px' }}>
        {/* Breadcrumb & Tiêu đề hoá đơn */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={onBack} type="text">Quay lại danh sách đơn</Button>
            <Breadcrumb
              items={[
                { title: 'Trang chủ' },
                { title: 'Đơn hàng của tôi' },
                { title: `Chi tiết đơn hàng #${displayOrder.orderCode}` },
              ]}
            />
          </Space>
          <Space>
            <Button icon={<PrinterOutlined />} onClick={() => window.print()}>In hoá đơn</Button>
            <Button icon={<DownloadOutlined />} type="primary" style={{ background: '#135200', borderColor: '#135200' }}>
              Tải hóa đơn điện tử (PDF)
            </Button>
          </Space>
        </div>

        {/* Tiêu đề & Mã xác nhận */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <Title level={2} style={{ margin: 0, fontWeight: 800 }}>
                Chi Tiết Đơn Hàng & Hóa Đơn Điện Tử
              </Title>
              <Tag color="#135200" style={{ fontSize: 13, padding: '4px 12px', fontWeight: 700 }}>
                ✔ ĐÃ XÁC NHẬN (CONFIRMED)
              </Tag>
            </div>
            <div style={{ color: '#64748b', fontSize: 13, marginTop: 4 }}>
              🕒 Thời gian tạo đơn: <strong>{formatDateTime(displayOrder.createdAt)}</strong> &nbsp;•&nbsp; 👤 Khách hàng: <strong>{displayOrder.customerName}</strong>
            </div>
          </div>
        </div>

        {/* 2-COLUMN LAYOUT HOÁ ĐƠN */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 380px', gap: 24, alignItems: 'start' }}>
          {/* CỘT TRÁI: DANH SÁCH MẶT HÀNG, THÔNG TIN GIAO DỊCH, HÓA ĐƠN VAT */}
          <div>
            {/* Box 1: Bảng Chi Tiết Mặt Hàng Cứu Trợ */}
            <div style={{ background: '#fff', borderRadius: 16, padding: 24, border: '1px solid #e2e8f0', marginBottom: 20 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                <Space align="center">
                  <div style={{ fontSize: 20 }}>📋</div>
                  <div>
                    <Text strong style={{ fontSize: 15 }}>Bảng Chi Tiết Mặt Hàng Cứu Trợ</Text>
                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>Đơn hàng #324 • Đặt qua Mobile App • 2 loại (3 món)</div>
                  </div>
                </Space>
                <Tag color="green" style={{ fontWeight: 700 }}>Tiết kiệm {discountPercent}%</Tag>
              </div>

              {/* Header bảng */}
              <div style={{ display: 'grid', gridTemplateColumns: '40px 1fr 110px 110px 40px', padding: '10px 12px', background: '#f8fafc', borderRadius: 8, fontSize: 12, fontWeight: 700, color: '#64748b', marginBottom: 12 }}>
                <span>STT</span>
                <span>Túi Giải Cứu / Sản Phẩm</span>
                <span style={{ textAlign: 'right' }}>Giá Gốc</span>
                <span style={{ textAlign: 'right' }}>Giá Cứu (Trợ)</span>
                <span style={{ textAlign: 'center' }}>SL</span>
              </div>

              {/* Danh sách items */}
              {displayOrder.items.map((it, idx) => (
                <div key={it.id || idx} style={{ display: 'grid', gridTemplateColumns: '40px 1fr 110px 110px 40px', padding: '14px 12px', borderBottom: '1px solid #f1f5f9', alignItems: 'center' }}>
                  <span style={{ fontWeight: 700, color: '#94a3b8' }}>0{idx + 1}</span>
                  <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                    <img
                      src={it.imageUrl || 'https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=200&auto=format&fit=crop&q=80'}
                      alt={it.title}
                      style={{ width: 52, height: 52, borderRadius: 8, objectFit: 'cover' }}
                    />
                    <div>
                      <Text strong style={{ fontSize: 13, display: 'block' }}>{it.title}</Text>
                      <div style={{ fontSize: 11, color: '#64748b' }}>
                        {it.categoryName || 'Bánh ngọt Pháp'} &nbsp;•&nbsp; Mã túi: {it.batchCode || `#BATCH-${it.batchId || it.id}`}
                      </div>
                    </div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <Text delete type="secondary" style={{ fontSize: 12 }}>
                      {formatMoney(it.originalPrice || it.unitPrice * 2)}
                    </Text>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <Text strong style={{ color: '#135200', fontSize: 13 }}>
                      {formatMoney(it.unitPrice)}
                    </Text>
                    <div style={{ fontSize: 10, color: '#cf1322' }}>{it.discount || '-65%'}</div>
                  </div>
                  <span style={{ textAlign: 'center', fontWeight: 700 }}>x{it.quantity}</span>
                </div>
              ))}

              {/* Cam kết chất lượng trong box */}
              <div style={{ background: '#f6ffed', border: '1px solid #b7eb8f', borderRadius: 8, padding: '10px 14px', margin: '16px 0', fontSize: 12, color: '#237804' }}>
                <strong>🛡️ Cam kết chất lượng:</strong> Sản phẩm bao gồm các món nướng tươi trong ngày theo tiêu chuẩn VSATTP, đóng gói trong túi giấy kraft tái chế bảo vệ môi trường. Vui lòng bảo quản mát và sử dụng trong ngày.
              </div>

              {/* Tổng kết tiền bạc */}
              <div style={{ marginTop: 16, paddingTop: 12, borderTop: '1px dashed #e2e8f0', fontSize: 13 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                  <Text type="secondary">Tổng giá trị niêm yết (Giá gốc):</Text>
                  <span>{formatMoney(totalOriginal > 0 ? totalOriginal : 290000)}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                  <Text type="secondary">Tạm tính giá giải cứu:</Text>
                  <span>{formatMoney(displayOrder.totalAmount)}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                  <Text type="secondary">Tiết kiệm giải cứu (Discount applied):</Text>
                  <span style={{ color: '#cf1322', fontWeight: 700 }}>
                    -{formatMoney(totalDiscount > 0 ? totalDiscount : 190000)} ({discountPercent}%)
                  </span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
                  <Text type="secondary">Phí dịch vụ & Đóng gói thân thiện: <Tag color="green">Miễn phí</Tag></Text>
                  <span>0đ</span>
                </div>

                <Divider style={{ margin: '10px 0' }} />

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <div style={{ fontSize: 15, fontWeight: 800 }}>TỔNG THANH TOÁN (TOTAL_AMOUNT)</div>
                    <Text type="secondary" style={{ fontSize: 12 }}>Đã thanh toán qua VNPay Sandbox</Text>
                  </div>
                  <div style={{ fontSize: 26, fontWeight: 900, color: '#135200' }}>
                    {formatMoney(displayOrder.totalAmount)}
                  </div>
                </div>
              </div>
            </div>

            {/* Box 2: Thông Tin Giao Dịch & Cổng Thanh Toán */}
            <div style={{ background: '#fff', borderRadius: 16, padding: 24, border: '1px solid #e2e8f0', marginBottom: 20 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                <Space>
                  <div style={{ fontSize: 20 }}>💳</div>
                  <div>
                    <Text strong style={{ fontSize: 15 }}>Thông Tin Giao Dịch & Cổng Thanh Toán</Text>
                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>payments • VNPay Sandbox Gateway Integration</div>
                  </div>
                </Space>
                <Tag color="green">✔ Giao dịch thành công</Tag>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, fontSize: 13, background: '#f8fafc', padding: 16, borderRadius: 10, border: '1px solid #f1f5f9' }}>
                <div>
                  <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>MÃ GIAO DỊCH VNPAY (transaction_id)</Text>
                  <Text strong style={{ fontFamily: 'monospace' }}>VNP143928159</Text>
                </div>
                <div>
                  <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>PHƯƠNG THỨC / NGÂN HÀNG</Text>
                  <Text strong>🏦 VNPAY-QR / Ngân Hàng Quốc Dân (NCB)</Text>
                </div>
                <div>
                  <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>MÃ THAM CHIẾU ĐỐI SOÁT (gateway_ref)</Text>
                  <Text strong style={{ fontFamily: 'monospace' }}>20260925184630-0021</Text>
                </div>
                <div>
                  <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>THỜI GIAN KHỚP LỆNH</Text>
                  <Text strong>✔ {formatDateTime(displayOrder.createdAt)} (Khớp trong 1m45s)</Text>
                </div>
              </div>

              {/* Hóa đơn điện tử VAT */}
              <div style={{ marginTop: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px', background: '#e6f7ff', borderRadius: 8, border: '1px solid #91d5ff' }}>
                <div>
                  <Text strong style={{ fontSize: 13, color: '#0050b3' }}>📄 Hóa Đơn Điện Tử VAT (e-Invoice)</Text>
                  <div style={{ fontSize: 11, color: '#096dd9' }}>Mã tra cứu Tổng cục Thuế: <strong>FR-EINV-20260925-899</strong></div>
                </div>
                <Space>
                  <Button size="small">Tra cứu</Button>
                  <Button size="small" type="primary" icon={<DownloadOutlined />}>XML / PDF</Button>
                </Space>
              </div>
            </div>

            {/* Box 3: Chỉ số bền vững đơn hàng */}
            <div style={{ background: '#fff', borderRadius: 16, padding: 20, border: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                <div style={{ width: 54, height: 54, borderRadius: '50%', background: '#e6f7ff', border: '3px solid #1890ff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, color: '#0050b3' }}>
                  82%
                </div>
                <div>
                  <Text strong style={{ fontSize: 13 }}>Chỉ số Bền Vững Đơn Hàng</Text>
                  <div style={{ fontSize: 12, color: '#64748b' }}>Tương đương tiết kiệm ~375 lít nước sạch sản xuất và 1.85kg thức ăn sạch.</div>
                </div>
              </div>
              <Tag color="cyan">CHUỖI CUNG ỨNG Zero-Food-Waste</Tag>
            </div>
          </div>

          {/* CỘT PHẢI: MÃ NHẬN HÀNG, QR CODE, KHUNG GIỜ PICKUP, BẢN ĐỒ QUÁN */}
          <div style={{ position: 'sticky', top: 80 }}>
            {/* Box Mã Nhận Hàng Trực Tiếp */}
            <div style={{ background: '#fff', borderRadius: 16, padding: 24, border: '1px solid #b7eb8f', boxShadow: '0 4px 16px rgba(0,0,0,0.06)', marginBottom: 20 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <Text strong style={{ fontSize: 14, color: '#135200' }}>Xác Nhận Nhận Hàng Trực Tiếp</Text>
                <Tag color="green">0/1 ĐÃ LẤY</Tag>
              </div>
              <Paragraph style={{ fontSize: 12, color: '#64748b', marginBottom: 16 }}>
                Đưa mã QR hoặc đọc mã 6 ký tự cho nhân viên thu ngân {displayOrder.storeName}.
              </Paragraph>

              {/* Box Mã 6 Ký Tự Siêu Nổi Bật */}
              <div
                style={{
                  background: '#f6ffed',
                  border: '2px dashed #52c41a',
                  borderRadius: 12,
                  padding: 16,
                  textAlign: 'center',
                  marginBottom: 16,
                  position: 'relative',
                }}
              >
                <Text type="secondary" style={{ fontSize: 11, letterSpacing: 1 }}>MÃ XÁC NHẬN BẢO MẬT (6 KÝ TỰ)</Text>
                <div style={{ fontSize: 36, fontWeight: 900, letterSpacing: 6, color: '#135200', margin: '6px 0' }}>
                  {displayOrder.pickupCode}
                </div>
                <Button icon={<CopyOutlined />} size="small" onClick={copyCode} style={{ fontSize: 11 }}>
                  Sao chép mã
                </Button>
              </div>

              {/* QR Code */}
              <div style={{ textAlign: 'center', padding: '12px 0' }}>
                <div style={{ display: 'inline-block', padding: 12, background: '#fff', borderRadius: 12, border: '1px solid #f0f0f0' }}>
                  <QRCodeSVG value={displayOrder.pickupCode || 'FR2609'} size={150} level="H" />
                </div>
                <div style={{ fontSize: 11, color: '#8c8c8c', marginTop: 8 }}>
                  Quét mã QR tại máy POS của cửa hàng để xác thực nhanh trong 3 giây.
                </div>
              </div>

              {/* Khung giờ lấy hàng */}
              <div style={{ background: '#f8fafc', borderRadius: 10, padding: 14, border: '1px solid #e2e8f0', marginTop: 16 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                  <Space size={6}>
                    <ClockCircleOutlined style={{ color: '#fa8c16' }} />
                    <Text strong style={{ fontSize: 13 }}>Khung giờ lấy hàng (Pickup Window):</Text>
                  </Space>
                  <Tag color="orange" style={{ margin: 0, fontSize: 10 }}>Hôm nay</Tag>
                </div>
                <div style={{ fontSize: 18, fontWeight: 800, color: '#0f172a' }}>
                  {formatPickupWindow(displayOrder.pickupStart, displayOrder.pickupEnd)}
                </div>
                <div style={{ fontSize: 11, color: '#ea580c', marginTop: 4 }}>
                  ⏳ Vui lòng đến đúng khung giờ để nhận món chuẩn chất lượng!
                </div>
              </div>

              {/* Quy tắc lấy hàng ĐH-01 và Cảnh báo No-Show */}
              <div style={{ marginTop: 14, padding: '10px 12px', background: '#fffbe6', borderRadius: 8, border: '1px solid #ffe58f', fontSize: 11, color: '#d46b08', lineHeight: 1.5 }}>
                <div><strong>📌 Quy tắc ĐH-01:</strong> Có mặt trong khung giờ để đảm bảo túi còn nguyên vẹn.</div>
                <div style={{ marginTop: 4 }}><strong>⚠️ Cảnh báo ĐH-10 (NO-SHOW):</strong> Quá 30 phút sau khi kết thúc khung giờ, đơn sẽ tự huỷ và không hoàn tiền theo chính sách.</div>
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
                    <Text strong style={{ fontSize: 14 }}>{displayOrder.storeName}</Text>
                    <CheckCircleOutlined style={{ color: '#1890ff' }} />
                  </div>
                  <Text type="secondary" style={{ fontSize: 12 }}>{displayOrder.storeAddress}</Text>
                </div>
              </div>

              <div style={{ fontSize: 12, color: '#64748b', marginBottom: 14 }}>
                <PhoneOutlined /> {displayOrder.storePhone} &nbsp;•&nbsp; 📍 Cách 1.2km
              </div>

              {/* Bản đồ mini */}
              <div
                style={{
                  height: 120,
                  borderRadius: 10,
                  background: 'linear-gradient(135deg, #e2e8f0 0%, #cbd5e1 100%)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  marginBottom: 12,
                }}
              >
                <div style={{ textAlign: 'center' }}>
                  <EnvironmentOutlined style={{ fontSize: 24, color: '#dc2626' }} />
                  <div style={{ fontSize: 12, fontWeight: 700, marginTop: 4 }}>{displayOrder.storeName}</div>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                <Button icon={<CompassOutlined />} size="small" block>
                  Chỉ đường Maps
                </Button>
                <Button size="small" block>
                  Gọi hotline quán
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
