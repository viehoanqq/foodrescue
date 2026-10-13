import { App, Button, Col, Form, Input, Row, TimePicker, Typography, Upload } from 'antd';
import {
  BankOutlined,
  CheckCircleFilled,
  EnvironmentOutlined,
  FileProtectOutlined,
  PlusOutlined,
  ShopOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Link } from 'react-router-dom';
import LocationPicker from '../components/LocationPicker';
import RuleNote from '../components/RuleNote';
import StoreShell from '../components/StoreShell';
import { S } from '../styles/stitchTheme';

const PITCH = [
  { title: 'Giảm tối thiểu 40%', text: 'Giá giải cứu tối đa bằng 60% giá gốc của túi.' },
  { title: 'Phí nền tảng mặc định 15%', text: 'Quản trị viên có thể điều chỉnh khi duyệt cửa hàng.' },
  { title: 'Chi trả theo kỳ', text: 'Doanh thu được chuyển khoản khi bạn bấm Yêu cầu rút tiền.' },
];

function Section({ icon, title, hint, children }) {
  return (
    <section style={{ paddingTop: 28, marginTop: 28, borderTop: `1px solid ${S.softBorder}` }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: hint ? 4 : 16 }}>
        <div
          aria-hidden="true"
          style={{ width: 36, height: 36, borderRadius: 10, background: S.primarySoft, color: S.primary, display: 'grid', placeItems: 'center', fontSize: 18 }}
        >
          {icon}
        </div>
        <Typography.Title level={4} style={{ margin: 0, color: S.navy }}>
          {title}
        </Typography.Title>
      </div>
      {hint && <Typography.Paragraph style={{ color: S.textMuted, margin: '0 0 16px 48px' }}>{hint}</Typography.Paragraph>}
      {children}
    </section>
  );
}

/** Đăng ký cửa hàng: /store/register (giao diện tĩnh S0; việc S2 sẽ nối POST /api/auth/register-store). */
export default function StoreRegisterPage() {
  const { message } = App.useApp();
  const [form] = Form.useForm();

  return (
    <StoreShell maxWidth={1120} rule="BR-02">
      <div className="fr-register">
        <aside className="fr-register__pitch">
          <div className="fr-register__pitch-inner">
            <div
              style={{
                alignSelf: 'flex-start',
                padding: '6px 14px',
                borderRadius: 999,
                background: 'rgba(255,255,255,0.14)',
                fontSize: 13,
                fontWeight: 700,
              }}
            >
              Dành cho cửa hàng, tiệm bánh, nhà hàng
            </div>
            <h1 style={{ margin: 0, fontSize: 'clamp(26px, 4vw, 34px)', lineHeight: 1.2, fontWeight: 800, color: '#fff' }}>
              Biến thực phẩm dư cuối ngày thành doanh thu
            </h1>
            <p style={{ margin: 0, lineHeight: 1.6, color: 'rgba(255,255,255,0.85)' }}>
              Đăng túi giải cứu kèm khung giờ lấy hàng, khách quanh bạn đặt và thanh toán qua VNPay, rồi đến quầy đọc mã nhận hàng.
            </p>
            <div style={{ display: 'grid', gap: 12 }}>
              {PITCH.map((p) => (
                <div
                  key={p.title}
                  style={{ padding: '14px 16px', borderRadius: 16, background: 'rgba(255,255,255,0.12)', border: '1px solid rgba(255,255,255,0.2)' }}
                >
                  <div style={{ fontWeight: 800, fontSize: 16, color: '#fff' }}>{p.title}</div>
                  <div style={{ fontSize: 13, color: 'rgba(255,255,255,0.8)', marginTop: 2 }}>{p.text}</div>
                </div>
              ))}
            </div>
            <div style={{ display: 'flex', gap: 10, paddingTop: 16, borderTop: '1px solid rgba(255,255,255,0.2)', fontSize: 13, lineHeight: 1.5, color: 'rgba(255,255,255,0.85)' }}>
              <CheckCircleFilled style={{ marginTop: 3, color: '#86efac' }} />
              <span>Hồ sơ được quản trị viên kiểm duyệt giấy phép vệ sinh an toàn thực phẩm trước khi cửa hàng hiển thị.</span>
            </div>
          </div>
        </aside>

        <main className="fr-register__main">
          <div style={{ color: S.primary, fontWeight: 800, fontSize: 13, letterSpacing: 0.4 }}>PHÂN HỆ CỬA HÀNG</div>
          <Typography.Title level={2} style={{ margin: '4px 0 8px', color: S.navy, fontWeight: 800 }}>
            Đăng ký cửa hàng
          </Typography.Title>
          <Typography.Paragraph style={{ color: S.textMuted }}>
            Cửa hàng chỉ đăng được túi và hiển thị cho khách sau khi quản trị viên duyệt hồ sơ.
          </Typography.Paragraph>
          <RuleNote code="BR-02">Mỗi chủ có một cửa hàng. Cửa hàng ở trạng thái chờ duyệt chưa được hiển thị trên trang chủ.</RuleNote>

          <Form
            form={form}
            layout="vertical"
            size="large"
            requiredMark={false}
            onFinish={() => message.info('Giao diện tĩnh, chưa nối API (việc S2)')}
            scrollToFirstError
            style={{ marginTop: 8 }}
          >
            <Section icon={<UserOutlined />} title="Tài khoản chủ cửa hàng">
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item name="fullName" label="Họ và tên" rules={[{ required: true, message: 'Nhập họ và tên' }]}>
                    <Input autoComplete="name" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="ownerPhone" label="Số điện thoại" rules={[{ required: true, message: 'Nhập số điện thoại' }]}>
                    <Input inputMode="tel" autoComplete="tel" />
                  </Form.Item>
                </Col>
                <Col xs={24}>
                  <Form.Item
                    name="email"
                    label="Email đăng nhập"
                    rules={[
                      { required: true, message: 'Nhập email' },
                      { type: 'email', message: 'Email không hợp lệ' },
                    ]}
                  >
                    <Input autoComplete="email" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item
                    name="password"
                    label="Mật khẩu"
                    rules={[
                      { required: true, message: 'Nhập mật khẩu' },
                      { min: 6, message: 'Mật khẩu tối thiểu 6 ký tự' },
                    ]}
                  >
                    <Input.Password autoComplete="new-password" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item
                    name="confirmPassword"
                    label="Nhập lại mật khẩu"
                    dependencies={['password']}
                    rules={[
                      { required: true, message: 'Nhập lại mật khẩu' },
                      ({ getFieldValue }) => ({
                        validator: (_, value) =>
                          !value || getFieldValue('password') === value
                            ? Promise.resolve()
                            : Promise.reject(new Error('Mật khẩu nhập lại không khớp')),
                      }),
                    ]}
                  >
                    <Input.Password autoComplete="new-password" />
                  </Form.Item>
                </Col>
              </Row>
            </Section>

            <Section icon={<ShopOutlined />} title="Thông tin cửa hàng">
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item name="storeName" label="Tên cửa hàng" rules={[{ required: true, message: 'Nhập tên cửa hàng' }]}>
                    <Input maxLength={150} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="storePhone" label="Số điện thoại cửa hàng" rules={[{ required: true, message: 'Nhập số điện thoại cửa hàng' }]}>
                    <Input inputMode="tel" />
                  </Form.Item>
                </Col>
                <Col xs={24}>
                  <Form.Item name="address" label="Địa chỉ" rules={[{ required: true, message: 'Nhập địa chỉ cửa hàng' }]}>
                    <Input />
                  </Form.Item>
                </Col>
                <Col xs={24}>
                  <Form.Item name="description" label="Giới thiệu ngắn">
                    <Input.TextArea rows={3} maxLength={500} showCount />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="openingHours" label="Giờ mở cửa" rules={[{ required: true, message: 'Chọn giờ mở và đóng cửa' }]}>
                    <TimePicker.RangePicker format="HH:mm" minuteStep={5} style={{ width: '100%' }} placeholder={['Mở cửa', 'Đóng cửa']} />
                  </Form.Item>
                </Col>
              </Row>
            </Section>

            <Section
              icon={<EnvironmentOutlined />}
              title="Vị trí trên bản đồ"
              hint="Khách hàng dùng vị trí này để tìm túi gần họ. Bấm vào bản đồ hoặc kéo ghim đến đúng cửa hàng."
            >
              <Form.Item name="location" style={{ marginBottom: 0 }} rules={[{ required: true, message: 'Chọn vị trí cửa hàng trên bản đồ' }]}>
                <LocationPicker />
              </Form.Item>
            </Section>

            <Section icon={<BankOutlined />} title="Tài khoản ngân hàng nhận tiền" hint="Dùng để chi trả doanh thu cho cửa hàng theo từng kỳ.">
              <Row gutter={16}>
                <Col xs={24} md={8}>
                  <Form.Item name="bankName" label="Ngân hàng" rules={[{ required: true, message: 'Nhập tên ngân hàng' }]}>
                    <Input />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name="bankAccountNo" label="Số tài khoản" rules={[{ required: true, message: 'Nhập số tài khoản' }]}>
                    <Input inputMode="numeric" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name="bankAccountName" label="Chủ tài khoản" rules={[{ required: true, message: 'Nhập tên chủ tài khoản' }]}>
                    <Input />
                  </Form.Item>
                </Col>
              </Row>
            </Section>

            <Section icon={<FileProtectOutlined />} title="Giấy phép vệ sinh an toàn thực phẩm" hint="Tải ảnh chụp rõ giấy phép (JPG hoặc PNG).">
              <Form.Item
                name="licenseImage"
                valuePropName="fileList"
                getValueFromEvent={(e) => e?.fileList}
                style={{ marginBottom: 0 }}
                rules={[{ required: true, message: 'Tải ảnh giấy phép' }]}
              >
                <Upload listType="picture-card" maxCount={1} accept="image/png,image/jpeg" beforeUpload={() => false}>
                  <div>
                    <PlusOutlined />
                    <div style={{ marginTop: 4 }}>Tải ảnh</div>
                  </div>
                </Upload>
              </Form.Item>
            </Section>

            <Button type="primary" htmlType="submit" size="large" block style={{ height: 52, marginTop: 32, fontSize: 16 }}>
              Gửi đăng ký
            </Button>
            <div style={{ textAlign: 'center', marginTop: 16, color: S.textMuted }}>
              Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
            </div>
          </Form>
        </main>
      </div>
    </StoreShell>
  );
}
