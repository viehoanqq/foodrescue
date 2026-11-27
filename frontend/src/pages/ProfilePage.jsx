import { useEffect, useState } from 'react';
import { Button, Card, Form, Input, InputNumber, Modal, Typography, message } from 'antd';
import { EnvironmentOutlined } from '@ant-design/icons';
import { useAuth } from '../auth/AuthProvider';
import { toFormErrors } from '../services/axios';
import GoogleSignInButton from '../components/GoogleSignInButton';
import './profilePage.css';

export default function ProfilePage() {
  const { user, updateProfile, linkGoogle } = useAuth();
  const [busy, setBusy] = useState(false);
  const [locating, setLocating] = useState(false);
  const [linkPassword, setLinkPassword] = useState('');
  const [linking, setLinking] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => { form.setFieldsValue({ fullName: user.fullName, phone: user.phone,
    address: user.address, latitude: user.latitude, longitude: user.longitude }); },
  [form, user.fullName, user.phone, user.address, user.latitude, user.longitude]);

  function locate() {
    if (!navigator.geolocation) { message.error('Trình duyệt không hỗ trợ lấy vị trí'); return; }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(position => {
      form.setFieldsValue({ latitude: Number(position.coords.latitude.toFixed(8)), longitude: Number(position.coords.longitude.toFixed(8)) });
      setLocating(false);
      message.success('Đã lấy tọa độ hiện tại. Hãy kiểm tra địa chỉ trước khi lưu.');
    }, () => { setLocating(false); message.error('Không lấy được vị trí. Hãy cấp quyền vị trí hoặc nhập tọa độ.'); },
    { enableHighAccuracy: true, timeout: 12000 });
  }

  async function submit(values) {
    setBusy(true);
    try {
      await updateProfile(values);
      message.success('Đã cập nhật hồ sơ');
    } catch (error) {
      if (error.details?.length) form.setFields(toFormErrors(error));
      message.error(error.message || 'Không cập nhật được hồ sơ');
    } finally {
      setBusy(false);
    }
  }

  function confirmGoogleLink(idToken) {
    if (!linkPassword) {
      message.warning('Nhập mật khẩu hiện tại trước khi liên kết Google.');
      return;
    }
    Modal.confirm({
      title: 'Liên kết tài khoản Google?',
      content: `Sau khi liên kết, bạn có thể dùng Google để đăng nhập vào tài khoản ${user.email}. Email Google phải trùng email này.`,
      okText: 'Liên kết', cancelText: 'Hủy',
      onOk: async () => {
        setLinking(true);
        try {
          await linkGoogle(idToken, linkPassword);
          setLinkPassword('');
          message.success('Đã liên kết tài khoản Google.');
        } catch (error) {
          message.error(error.message || 'Không thể liên kết Google.');
          throw error;
        } finally {
          setLinking(false);
        }
      },
    });
  }

  return <div><Card className="profile-card">
    <Typography.Title level={4}>Hồ sơ cá nhân</Typography.Title>
    <Typography.Paragraph type="secondary">Thông tin này được dùng để hiển thị tên và liên hệ với bạn.</Typography.Paragraph>
    <Form form={form} layout="vertical" onFinish={(values) => Modal.confirm({ title: 'Lưu thay đổi hồ sơ?',
      content: 'Tên, số điện thoại và địa chỉ mới sẽ được dùng cho các lần đặt túi tiếp theo.',
      okText: 'Lưu thay đổi', cancelText: 'Kiểm tra lại', onOk: () => submit(values) })}>
      <Form.Item label="Email"><Input value={user.email} disabled /></Form.Item>
      <Form.Item name="fullName" label="Họ tên" rules={[{ required: true, max: 100 }]}><Input /></Form.Item>
      <Form.Item name="phone" label="Số điện thoại" rules={[{ required: true, max: 20 }]}><Input /></Form.Item>
      {user.role === 'CUSTOMER' && <>
        <Typography.Title level={5}>Địa chỉ của tôi</Typography.Title>
        <Typography.Paragraph type="secondary">Nhập địa chỉ để liên hệ. Lấy vị trí hiện tại hoặc nhập tọa độ để xem khoảng cách đến cửa hàng. Khoảng cách hiển thị là đường thẳng.</Typography.Paragraph>
        <Form.Item name="address" label="Địa chỉ" rules={[{ max: 255 }]}><Input maxLength={255} placeholder="Số nhà, đường, phường, quận, thành phố" /></Form.Item>
        <div className="profile-location-fields"><Form.Item name="latitude" label="Vĩ độ" rules={[{ type: 'number', min: -90, max: 90 }]}><InputNumber min={-90} max={90} precision={8} /></Form.Item>
          <Form.Item name="longitude" label="Kinh độ" rules={[{ type: 'number', min: -180, max: 180 }]}><InputNumber min={-180} max={180} precision={8} /></Form.Item></div>
        <Button className="profile-locate-button" icon={<EnvironmentOutlined />} loading={locating} onClick={locate}>Lấy vị trí hiện tại</Button>
      </>}
      <div className="profile-form-actions"><Button type="primary" htmlType="submit" loading={busy}>Lưu thay đổi</Button></div>
    </Form>
  </Card>
  {['CUSTOMER', 'STORE_OWNER'].includes(user.role) && <Card className="profile-card" style={{ marginTop: 20 }}>
    <Typography.Title level={5}>Đăng nhập bằng Google</Typography.Title>
    {user.googleLinked ? <Typography.Paragraph>Google đã được liên kết với tài khoản này.</Typography.Paragraph> : <>
      <Typography.Paragraph type="secondary">
        Nhập mật khẩu hiện tại, sau đó chọn tài khoản Google có cùng email {user.email}.
      </Typography.Paragraph>
      <Input.Password style={{ maxWidth: 400, marginBottom: 16 }} value={linkPassword}
        onChange={(event) => setLinkPassword(event.target.value)}
        placeholder="Mật khẩu hiện tại" autoComplete="current-password" />
      <GoogleSignInButton onCredential={confirmGoogleLink} text="continue_with" disabled={linking} />
    </>}
  </Card>}
  </div>;
}
