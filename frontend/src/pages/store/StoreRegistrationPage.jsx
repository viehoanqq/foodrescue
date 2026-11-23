import { useEffect, useState } from 'react';
import { Alert, Button, Card, Checkbox, Col, Descriptions, Form, Input, InputNumber, Modal, Row, Space, Spin, Tag, TimePicker, Typography, Upload, message } from 'antd';
import { BankOutlined, CheckCircleOutlined, EnvironmentOutlined, FileImageOutlined, ShopOutlined, UploadOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { storeApi } from '../../services/storeApi';
import { useAuth } from '../../auth/AuthProvider';
import './storePages.css';

const statusText = { PENDING: 'Đang chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Bị từ chối', LOCKED: 'Đã khóa' };
const statusColor = { PENDING: 'gold', APPROVED: 'green', REJECTED: 'red', LOCKED: 'red' };
const display = (value) => value == null || value === '' ? 'Chưa cung cấp' : value;
const imageTypes = ['image/jpeg', 'image/png', 'image/webp'];

function validImage(file) {
  if (!imageTypes.includes(file.type)) { message.error('Chỉ nhận ảnh JPEG, PNG hoặc WebP'); return false; }
  if (file.size > 5 * 1024 * 1024) { message.error('Ảnh không được vượt quá 5 MB'); return false; }
  return true;
}

export default function StoreRegistrationPage() {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const [form] = Form.useForm();
  const [existing, setExisting] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [license, setLicense] = useState(null);
  const [image, setImage] = useState(null);

  useEffect(() => {
    let active = true;
    storeApi.registration().then((result) => { if (active) setExisting(result); })
      .catch((failure) => { if (active && failure.status !== 404) setError(failure.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  function chooseFile(file, setFile) {
    if (validImage(file)) setFile(file);
    return false;
  }

  async function submit(values) {
    if (!license) { message.error('Vui lòng tải ảnh giấy phép kinh doanh'); return; }
    const data = {
      ...values,
      storeName: values.storeName.trim(), storePhone: values.storePhone.trim(),
      address: values.address.trim(), bankName: values.bankName.trim(),
      bankAccountNo: values.bankAccountNo.trim(), bankAccountName: values.bankAccountName.trim(),
      openTime: values.openTime.format('HH:mm'), closeTime: values.closeTime.format('HH:mm'),
    };
    delete data.confirmed;
    if (data.closeTime <= data.openTime) { message.error('Giờ đóng cửa phải sau giờ mở cửa'); return; }
    Modal.confirm({
      title: 'Xác nhận gửi hồ sơ cửa hàng',
      width: 560,
      content: <div className="store-confirm"><p>Kiểm tra thông tin trước khi gửi Admin duyệt. Sau khi gửi, bạn chưa thể sửa hồ sơ cho đến khi có chức năng cập nhật.</p><Descriptions size="small" column={1} items={[
        { key: 'name', label: 'Cửa hàng', children: data.storeName },
        { key: 'address', label: 'Địa chỉ', children: data.address },
        { key: 'hours', label: 'Giờ hoạt động', children: `${data.openTime} – ${data.closeTime}` },
        { key: 'bank', label: 'Tài khoản nhận tiền', children: `${data.bankName} · ${data.bankAccountNo}` },
        { key: 'license', label: 'Giấy phép', children: license.name },
      ]} /></div>,
      okText: 'Xác nhận gửi', cancelText: 'Kiểm tra lại',
      async onOk() {
        setSubmitting(true);
        try {
          await storeApi.register(data, license, image);
          setExisting(await storeApi.registration());
          message.success('Đã gửi hồ sơ. Vui lòng chờ Admin duyệt.');
        } catch (failure) {
          message.error(failure.message || 'Không gửi được hồ sơ');
          throw failure;
        } finally { setSubmitting(false); }
      },
    });
  }

  if (loading) return <div className="store-page store-center"><Spin size="large" /></div>;
  return <main className="store-page">
    <div className="store-page-heading"><span>HỢP TÁC CÙNG FOODRESCUE</span><h1>Đăng ký cửa hàng</h1><p>Đưa thực phẩm còn tốt đến nhiều khách hàng hơn. Hồ sơ được xem xét trước khi cửa hàng bắt đầu bán.</p></div>
    {error && <Alert type="error" showIcon message="Không tải được hồ sơ" description={error} style={{ marginBottom: 20 }} />}
    {existing ? <Card className="store-card"><Space direction="vertical" size="middle"><CheckCircleOutlined className="store-success-icon" /><h2>Hồ sơ đã được tiếp nhận</h2><Tag color={statusColor[existing.status]}>{statusText[existing.status] || display(existing.status)}</Tag><p>Mã hồ sơ: {display(existing.storeId)}. Vui lòng theo dõi trạng thái tại đây.</p>{existing.rejectReason && <Alert type="error" showIcon message="Lý do từ chối" description={existing.rejectReason} />}</Space>
      <Descriptions style={{ marginTop: 24 }} bordered column={{ xs: 1, sm: 2 }} items={[
        { key: 'name', label: 'Tên cửa hàng', children: display(existing.name) },
        { key: 'phone', label: 'Số điện thoại', children: display(existing.phone) },
        { key: 'address', label: 'Địa chỉ', span: 2, children: display(existing.address) },
        { key: 'hours', label: 'Giờ hoạt động', children: existing.openTime && existing.closeTime ? `${existing.openTime.slice(0, 5)} – ${existing.closeTime.slice(0, 5)}` : 'Chưa cung cấp' },
        { key: 'coords', label: 'Tọa độ', children: existing.latitude != null && existing.longitude != null ? `${existing.latitude}, ${existing.longitude}` : 'Chưa cung cấp' },
        { key: 'description', label: 'Giới thiệu', span: 2, children: display(existing.description) },
        { key: 'bank', label: 'Ngân hàng', children: display(existing.bankName) },
        { key: 'account', label: 'Số tài khoản', children: display(existing.bankAccountNoMasked) },
        { key: 'holder', label: 'Tên chủ tài khoản', children: display(existing.bankAccountName) },
        { key: 'date', label: 'Ngày nộp', children: existing.createdAt ? existing.createdAt.replace('T', ' ').slice(0, 16) : 'Chưa có' },
      ]} />{existing.imageUrl && <img className="store-detail-image" style={{ marginTop: 20 }} src={existing.imageUrl} alt={existing.name} />}{existing.status === 'APPROVED' && <div style={{ marginTop: 20 }}><Button type="primary" onClick={() => { logout(); navigate('/store/login'); }}>Đăng nhập trang quản trị cửa hàng</Button></div>}</Card> : error ? <Button onClick={() => window.location.reload()}>Tải lại hồ sơ</Button> :
      <Form form={form} layout="vertical" onFinish={submit} scrollToFirstError initialValues={{ openTime: null, closeTime: null }}>
        <Card className="store-card" title={<><ShopOutlined /> Thông tin cửa hàng</>}>
          <Row gutter={20}><Col xs={24} md={12}><Form.Item name="storeName" label="Tên cửa hàng" rules={[{ required: true, whitespace: true, message: 'Nhập tên cửa hàng' }, { max: 150 }]}><Input maxLength={150} placeholder="Ví dụ: Bánh mì An Phát" /></Form.Item></Col><Col xs={24} md={12}><Form.Item name="storePhone" label="Số điện thoại cửa hàng" rules={[{ required: true, message: 'Nhập số điện thoại' }, { pattern: /^0\d{9,10}$/, message: 'Nhập số điện thoại hợp lệ' }]}><Input maxLength={20} placeholder="09xxxxxxxx" /></Form.Item></Col></Row>
          <Form.Item name="description" label="Giới thiệu cửa hàng"><Input.TextArea maxLength={1000} showCount rows={3} placeholder="Cửa hàng bán những gì?" /></Form.Item>
          <Row gutter={20}><Col xs={24} md={16}><Form.Item name="address" label="Địa chỉ" rules={[{ required: true, whitespace: true, message: 'Nhập địa chỉ' }, { max: 255 }]}><Input maxLength={255} prefix={<EnvironmentOutlined />} placeholder="Số nhà, đường, phường, quận, thành phố" /></Form.Item></Col><Col xs={12} md={4}><Form.Item name="openTime" label="Mở cửa" rules={[{ required: true, message: 'Chọn giờ mở' }]}><TimePicker format="HH:mm" minuteStep={5} style={{ width: '100%' }} /></Form.Item></Col><Col xs={12} md={4}><Form.Item name="closeTime" label="Đóng cửa" rules={[{ required: true, message: 'Chọn giờ đóng' }]}><TimePicker format="HH:mm" minuteStep={5} style={{ width: '100%' }} /></Form.Item></Col></Row>
          <Row gutter={20}><Col xs={12}><Form.Item name="latitude" label="Vĩ độ" extra="Tọa độ giúp khách tìm cửa hàng gần họ." rules={[{ required: true, message: 'Nhập vĩ độ' }]}><InputNumber min={-90} max={90} precision={8} style={{ width: '100%' }} placeholder="10.776890" /></Form.Item></Col><Col xs={12}><Form.Item name="longitude" label="Kinh độ" rules={[{ required: true, message: 'Nhập kinh độ' }]}><InputNumber min={-180} max={180} precision={8} style={{ width: '100%' }} placeholder="106.700806" /></Form.Item></Col></Row>
        </Card>
        <Card className="store-card" title={<><FileImageOutlined /> Hình ảnh và giấy phép</>}>
          <Row gutter={20}><Col xs={24} md={12}><p>Ảnh giấy phép kinh doanh <strong>*</strong></p><Upload accept="image/jpeg,image/png,image/webp" beforeUpload={(file) => chooseFile(file, setLicense)} fileList={license ? [license] : []} onRemove={() => setLicense(null)} maxCount={1}><Button icon={<UploadOutlined />}>Chọn ảnh giấy phép</Button></Upload><Typography.Text type="secondary">Chỉ Admin xem ảnh này. JPEG, PNG hoặc WebP, tối đa 5 MB.</Typography.Text></Col><Col xs={24} md={12}><p>Ảnh cửa hàng</p><Upload accept="image/jpeg,image/png,image/webp" beforeUpload={(file) => chooseFile(file, setImage)} fileList={image ? [image] : []} onRemove={() => setImage(null)} maxCount={1}><Button icon={<UploadOutlined />}>Chọn ảnh cửa hàng</Button></Upload><Typography.Text type="secondary">Ảnh này hiển thị công khai sau khi cửa hàng được duyệt.</Typography.Text></Col></Row>
        </Card>
        <Card className="store-card" title={<><BankOutlined /> Tài khoản nhận tiền</>}>
          <Row gutter={20}><Col xs={24} md={8}><Form.Item name="bankName" label="Ngân hàng" rules={[{ required: true, whitespace: true, message: 'Nhập ngân hàng' }, { max: 100 }]}><Input maxLength={100} /></Form.Item></Col><Col xs={24} md={8}><Form.Item name="bankAccountNo" label="Số tài khoản" rules={[{ required: true, message: 'Nhập số tài khoản' }, { pattern: /^\d{6,30}$/, message: 'Số tài khoản gồm 6–30 chữ số' }]}><Input maxLength={30} /></Form.Item></Col><Col xs={24} md={8}><Form.Item name="bankAccountName" label="Tên chủ tài khoản" rules={[{ required: true, whitespace: true, message: 'Nhập tên chủ tài khoản' }, { max: 100 }]}><Input maxLength={100} /></Form.Item></Col></Row>
          <Form.Item name="confirmed" valuePropName="checked" rules={[{ validator: (_, checked) => checked ? Promise.resolve() : Promise.reject(new Error('Bạn cần xác nhận thông tin')) }]}><Checkbox>Tôi xác nhận thông tin cửa hàng, giấy phép và tài khoản nhận tiền là chính xác.</Checkbox></Form.Item>
          <Button type="primary" size="large" htmlType="submit" loading={submitting}>Kiểm tra và gửi hồ sơ</Button>
        </Card>
      </Form>}
  </main>;
}
