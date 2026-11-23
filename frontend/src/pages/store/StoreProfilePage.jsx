import { useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Descriptions, Form, Image, Input, InputNumber, Modal, Row, Spin, Tag, TimePicker, message } from 'antd';
import dayjs from 'dayjs';
import { storeApi } from '../../services/storeApi';
import './storePages.css';

const statusText = { PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Bị từ chối', LOCKED: 'Đã khóa' };

export default function StoreProfilePage() {
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [editOpen, setEditOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  useEffect(() => {
    let active = true;
    storeApi.profile().then((result) => { if (active) setProfile(result); })
      .catch((failure) => { if (active) setError(failure.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);
  function openEditor() {
    form.setFieldsValue({
      name: profile.name, description: profile.description, phone: profile.phone,
      address: profile.address, latitude: Number(profile.latitude), longitude: Number(profile.longitude),
      openTime: dayjs(`2000-01-01T${profile.openTime}`),
      closeTime: dayjs(`2000-01-01T${profile.closeTime}`),
      bankName: profile.bankName, bankAccountNo: '', bankAccountName: profile.bankAccountName,
    });
    setEditOpen(true);
  }
  async function save(values) {
    const request = {
      ...values, openTime: values.openTime.format('HH:mm'), closeTime: values.closeTime.format('HH:mm'),
      bankAccountNo: values.bankAccountNo?.trim() || null,
    };
    if (request.closeTime <= request.openTime) {
      message.error('Giờ đóng cửa phải sau giờ mở cửa');
      return;
    }
    setSaving(true);
    try {
      const updated = await storeApi.updateProfile(request);
      setProfile(updated);
      setEditOpen(false);
      message.success('Đã cập nhật hồ sơ cửa hàng');
    } catch (failure) { message.error(failure.message || 'Không cập nhật được hồ sơ'); }
    finally { setSaving(false); }
  }
  return <main className="store-page"><div className="store-page-heading"><span>CỬA HÀNG CỦA BẠN</span><h1>Hồ sơ cửa hàng</h1><p>Thông tin đang lưu trong hệ thống.</p></div>
    {loading ? <div className="store-center"><Spin size="large" /></div> : error ? <Alert type="error" showIcon message={error} /> : <>
      {profile.status !== 'APPROVED' && <Alert style={{ marginBottom: 20 }} type="warning" showIcon message={`Trạng thái: ${statusText[profile.status] || profile.status}`} description={profile.rejectReason || 'Cửa hàng chưa thể bán túi giải cứu.'} />}
      <Card className="store-card" title="Thông tin cửa hàng" extra={<Button type="primary" onClick={openEditor}>Chỉnh sửa hồ sơ</Button>}>
        {profile.imageUrl && <Image src={profile.imageUrl} alt={profile.name} className="store-profile-image" />}
        <Descriptions column={{ xs: 1, sm: 2 }} bordered items={[
          { key: 'name', label: 'Tên cửa hàng', children: profile.name },
          { key: 'status', label: 'Trạng thái', children: <Tag color={profile.status === 'APPROVED' ? 'green' : 'gold'}>{statusText[profile.status] || profile.status}</Tag> },
          { key: 'phone', label: 'Số điện thoại', children: profile.phone },
          { key: 'hours', label: 'Giờ hoạt động', children: `${profile.openTime?.slice(0, 5)} – ${profile.closeTime?.slice(0, 5)}` },
          { key: 'address', label: 'Địa chỉ', span: 2, children: profile.address },
          { key: 'coords', label: 'Tọa độ', children: `${profile.latitude}, ${profile.longitude}` },
          { key: 'rate', label: 'Hoa hồng', children: `${profile.commissionRate}%` },
          { key: 'description', label: 'Giới thiệu', span: 2, children: profile.description || 'Chưa có' },
        ]} />
      </Card>
      <Card className="store-card" title="Tài khoản nhận tiền"><Descriptions column={{ xs: 1, sm: 2 }} bordered items={[
        { key: 'bank', label: 'Ngân hàng', children: profile.bankName },
        { key: 'account', label: 'Số tài khoản', children: profile.bankAccountNoMasked },
        { key: 'holder', label: 'Chủ tài khoản', children: profile.bankAccountName },
      ]} /></Card>
      <Modal title="Chỉnh sửa hồ sơ cửa hàng" open={editOpen} width={760} onCancel={() => setEditOpen(false)}
        onOk={() => form.submit()} okText="Lưu thay đổi" confirmLoading={saving} destroyOnClose>
        <Form form={form} layout="vertical" onFinish={save}>
          <Row gutter={16}><Col xs={24} sm={12}><Form.Item name="name" label="Tên cửa hàng" rules={[{ required: true, whitespace: true }, { max: 150 }]}><Input maxLength={150} /></Form.Item></Col>
            <Col xs={24} sm={12}><Form.Item name="phone" label="Số điện thoại" rules={[{ required: true, whitespace: true }, { max: 20 }]}><Input maxLength={20} /></Form.Item></Col></Row>
          <Form.Item name="description" label="Giới thiệu" rules={[{ max: 1000 }]}><Input.TextArea rows={3} maxLength={1000} showCount /></Form.Item>
          <Form.Item name="address" label="Địa chỉ" rules={[{ required: true, whitespace: true }, { max: 255 }]}><Input maxLength={255} /></Form.Item>
          <Row gutter={16}><Col xs={12}><Form.Item name="latitude" label="Vĩ độ" rules={[{ required: true }]}><InputNumber min={-90} max={90} precision={8} style={{ width: '100%' }} /></Form.Item></Col>
            <Col xs={12}><Form.Item name="longitude" label="Kinh độ" rules={[{ required: true }]}><InputNumber min={-180} max={180} precision={8} style={{ width: '100%' }} /></Form.Item></Col></Row>
          <Row gutter={16}><Col xs={12}><Form.Item name="openTime" label="Mở cửa" rules={[{ required: true }]}><TimePicker format="HH:mm" style={{ width: '100%' }} /></Form.Item></Col>
            <Col xs={12}><Form.Item name="closeTime" label="Đóng cửa" rules={[{ required: true }]}><TimePicker format="HH:mm" style={{ width: '100%' }} /></Form.Item></Col></Row>
          <Row gutter={16}><Col xs={24} sm={12}><Form.Item name="bankName" label="Ngân hàng" rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input maxLength={100} /></Form.Item></Col>
            <Col xs={24} sm={12}><Form.Item name="bankAccountName" label="Tên chủ tài khoản" rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input maxLength={100} /></Form.Item></Col></Row>
          <Form.Item name="bankAccountNo" label="Số tài khoản mới" extra={`Để trống nếu giữ số tài khoản hiện tại (${profile.bankAccountNoMasked || 'đã lưu'}).`}
            rules={[{ pattern: /^\d{6,30}$/, message: 'Số tài khoản gồm 6–30 chữ số' }]}><Input placeholder="Chỉ nhập khi muốn thay đổi" maxLength={30} /></Form.Item>
        </Form>
      </Modal>
    </>}
  </main>;
}
